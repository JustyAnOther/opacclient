/**
 * Copyright (C) 2013 by Raphael Michel under the MIT license:
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */
package de.geeksfactory.opacclient.frontend;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.loader.app.LoaderManager;
import androidx.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.cursoradapter.widget.SimpleCursorAdapter;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import de.geeksfactory.opacclient.OpacClient;
import de.geeksfactory.opacclient.R;
import de.geeksfactory.opacclient.frontend.OpacActivity.AccountSelectedListener;
import de.geeksfactory.opacclient.objects.Account;
import de.geeksfactory.opacclient.objects.Copy;
import de.geeksfactory.opacclient.objects.SearchResult;
import de.geeksfactory.opacclient.objects.Starred;
import de.geeksfactory.opacclient.searchfields.DropdownSearchField;
import de.geeksfactory.opacclient.searchfields.SearchField;
import de.geeksfactory.opacclient.searchfields.SearchField.Meaning;
import de.geeksfactory.opacclient.searchfields.SearchQuery;
import de.geeksfactory.opacclient.storage.Branch;
import de.geeksfactory.opacclient.storage.JsonSearchFieldDataSource;
import de.geeksfactory.opacclient.storage.SearchFieldDataSource;
import de.geeksfactory.opacclient.storage.StarContentProvider;
import de.geeksfactory.opacclient.storage.StarDataSource;
import de.geeksfactory.opacclient.storage.StarDatabase;
import de.geeksfactory.opacclient.storage.Starred;
import de.geeksfactory.opacclient.utils.CompatibilityUtils;

public class StarredFragment extends Fragment implements
        LoaderCallbacks<Cursor>, AccountSelectedListener {

    public static final String STATE_ACTIVATED_POSITION = "activated_position";
    public static final String STATE_FILTER_BRANCH = "filter_branch";

    private static final String JSON_LIBRARY_NAME = "library_name";
    private static final String JSON_STARRED_LIST = "starred_list";
    private static final String JSON_ITEM_MNR = "item_mnr";
    private static final String JSON_ITEM_TITLE = "item_title";
    private static final String JSON_ITEM_MEDIATYPE = "item_mediatype";
    private static final String JSON_ITME_BRANCHES = "item_branches";
    private static final int REQUEST_CODE_EXPORT = 123;
    private static final int REQUEST_CODE_IMPORT = 124;
    private static final int LOADER_ID = 0; // !=1 wie bei History

    protected View view;
    protected OpacClient app;
    private ItemListAdapter adapter;
    private Callback callback;
    private ListView listView;
    private int activatedPosition = ListView.INVALID_POSITION;
    private TextView tvWelcome;

    private int currentFilterBranchId;
    private MenuItem currentFilterItem;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        setHasOptionsMenu(true);

        view = inflater.inflate(R.layout.fragment_starred, container, false);
        app = (OpacClient) getActivity().getApplication();

        adapter = new ItemListAdapter();

        listView = (ListView) view.findViewById(R.id.lvStarred);
        tvWelcome = (TextView) view.findViewById(R.id.tvWelcome);

        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                Starred item = (Starred) view.findViewById(R.id.ivDelete)
                                             .getTag();
                if (item.getMNr() == null || item.getMNr().equals("null")
                        || item.getMNr().equals("")) {

                    SharedPreferences sp = PreferenceManager
                            .getDefaultSharedPreferences(getActivity());
                    List<SearchQuery> query = new ArrayList<>();
                    List<SearchField> fields = new JsonSearchFieldDataSource(
                            app).getSearchFields(app.getLibrary().getIdent());
                    if (fields != null) {
                        SearchField title_field = null, free_field = null;
                        for (SearchField field : fields) {
                            if (field.getMeaning() == Meaning.TITLE) {
                                title_field = field;
                            } else if (field.getMeaning() == Meaning.FREE) {
                                free_field = field;
                            } else if (field.getMeaning() == Meaning.HOME_BRANCH) {
                                query.add(new SearchQuery(field, sp.getString(
                                        OpacClient.PREF_HOME_BRANCH_PREFIX
                                                + app.getAccount().getId(),
                                        null)));
                            }
                        }
                        if (title_field != null) {
                            query.add(new SearchQuery(title_field, item
                                    .getTitle()));
                        } else if (free_field != null) {
                            query.add(new SearchQuery(free_field, item
                                    .getTitle()));
                        }
                        app.startSearch(getActivity(), query);
                    } else {
                        Toast.makeText(getActivity(), R.string.no_search_cache,
                                Toast.LENGTH_LONG).show();
                    }
                } else {
                    getActivity().invalidateOptionsMenu();
                    callback.showDetail(item.getMNr(), item.getMediaType());
                }
            }
        });
        listView.setClickable(true);
        listView.setTextFilterEnabled(true);

        LoaderManager.getInstance(this)
                     .initLoader(LOADER_ID, null, this);
        listView.setAdapter(adapter);

        SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(getContext());

        currentFilterBranchId = sp.getInt(STATE_FILTER_BRANCH, 0);
        if (sp.contains(STATE_ACTIVATED_POSITION)) {
            setActivatedPosition(sp
                    .getInt(STATE_ACTIVATED_POSITION, AdapterView.INVALID_POSITION));
        }

        if (savedInstanceState != null) {
            // Restore the previously serialized activated item position.
            if (savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
                setActivatedPosition(savedInstanceState
                        .getInt(STATE_ACTIVATED_POSITION));
            }

            if (savedInstanceState.containsKey(STATE_FILTER_BRANCH)) {
                currentFilterBranchId = savedInstanceState.getInt(STATE_FILTER_BRANCH);
            }
        }

        setActivateOnItemClick(((OpacActivity) getActivity()).isTablet());

        return view;
    }

    @Override
    public void onCreateOptionsMenu(android.view.Menu menu,
            MenuInflater inflater) {

        inflater.inflate(R.menu.activity_starred, menu);

        // FilterMenu um Branches erweitern
        if (true) {
            addSubMenuBranchesFromDb(menu);
        } else {
            addSubMenuBranchesFromSearchField(menu);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    private void addSubMenuBranchesFromDb(android.view.Menu menu) {
        MenuItem itemFilter = menu.findItem(R.id.action_filter);
        // menu.addSubMenu(Menu.NONE, R.id.action_filter, Menu.NONE,"Menu1");
        SubMenu subMenu = itemFilter.getSubMenu();

        StarDataSource data = new StarDataSource(getActivity());
        String bib = app.getLibrary().getIdent();
        List<Branch> branches = data.getStarredBranches(bib);

        if ((null == branches) || branches.isEmpty()) {
            itemFilter.setVisible(false);
            return;
        }

        final int groupId = Menu.NONE;
        // final int groupId = R.id.group_filter;
        // subMenu.setGroupCheckable(groupId, true, true);
        // final RadioGroup radioGroup = (RadioGroup) menu.findViewById(R.id.group_filter);
        for (Branch branch : branches) {
            String text = String.format("%s (%d)", branch.getBranch(), branch.getCount());
            MenuItem menuItem = subMenu.add(groupId, branch.getId(), Menu.NONE, text);
            menuItem.setCheckable(true);
            if (branch.getId() == currentFilterBranchId) {
                menuItem.setChecked(true);
                currentFilterItem = menuItem;
            }
        }
    }

    private void addSubMenuBranchesFromSearchField(android.view.Menu menu) {
        MenuItem itemFilter = menu.findItem(R.id.action_filter);
        // menu.addSubMenu(Menu.NONE, R.id.action_filter, Menu.NONE,"Menu1");
        SubMenu subMenu = itemFilter.getSubMenu();
        SearchField field = findSearchField(Meaning.BRANCH);
        if (null == field) {
            itemFilter.setVisible(false);
            return;
        }

        if (!(field instanceof DropdownSearchField)) {
            itemFilter.setVisible(false);
            return;
        }

        DropdownSearchField ddSearchField = (DropdownSearchField) field;
        if ((ddSearchField.getDropdownValues() == null) || ddSearchField.getDropdownValues().isEmpty()){
            itemFilter.setVisible(false);
            return;
        }

        // final int groupId = Menu.NONE;
        // final int groupId = R.id.group_filter;
        // subMenu.setGroupCheckable(groupId, true, true);
        // final RadioGroup radioGroup = (RadioGroup) menu.findViewById(R.id.group_filter);
        for (DropdownSearchField.Option value : ddSearchField.getDropdownValues()) {
            // MenuItem menuItem = subMenu.add(groupId, R.id.action_filter_branch, Menu.NONE,value.getValue());
            MenuItem menuItem = subMenu.add(value.getValue());
            menuItem.setCheckable(true);
        }
    }

    private SearchField findSearchField(Meaning meaning) {
        SearchFieldDataSource dataSource = new JsonSearchFieldDataSource(app);
        List<SearchField> fields = dataSource.getSearchFields(app.getLibrary().getIdent());

        for (SearchField field : fields) {
            if (field.getMeaning() == meaning) {
                return field;
            }
        }
        return null;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_export) {
            share();
            return true;
        } else if (item.getItemId() == R.id.action_export_to_storage) {
            exportToStorage();
            return true;
        } else if (item.getItemId() == R.id.action_import_from_storage) {
            importFromStorage();
            getActivity().invalidateOptionsMenu();
            return true;
        } else if (item.getItemId() == R.id.action_filter) {
            return super.onOptionsItemSelected(item);
//        } else if (item.getItemId() == R.id.action_filter_branch) {
//            return super.onOptionsItemSelected(item);
        } else {
            // Hier FilterSubMenu
            if (currentFilterItem != null) {
                currentFilterItem.setChecked(false);
            }
            if (item.getItemId() == currentFilterBranchId) {
                // clear selection
                currentFilterBranchId = 0;
                currentFilterItem = null;
            } else {
                // new selection
                currentFilterBranchId = item.getItemId();

                currentFilterItem = item;
                currentFilterItem.setChecked(true);

                // TODO filtertimestamp updaten
                StarDataSource data = new StarDataSource(getActivity());
                data.updateBranchFiltertimestamp(item.getItemId(), new Date().getTime());

            }
            getActivity().invalidateOptionsMenu();
            getActivity().getSupportLoaderManager().restartLoader(LOADER_ID, null, this);
            return true;
        }
    }

    @Override
    public void accountSelected(Account account) {
        LoaderManager.getInstance(this).restartLoader(0, null, this);
    }

    //Added code to show SnackBar when clicked on Remove button in Favorites screen
    public void remove(Starred item) {
        Snackbar snackbar =
                Snackbar.make(view, getString(R.string.starred_removed), Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.starred_removed_undo, new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                /*
                StarDataSource data = new StarDataSource(getActivity());
                String bib = app.getLibrary().getIdent();
                data.star(sItem.getMNr(), sItem.getTitle(), bib, sItem.getMediaType());
                 */
            }
        });

        // https://stackoverflow.com/questions/30926380/how-can-i-be-notified-when-a-snackbar-has-dismissed-itself
        snackbar.addCallback(new Snackbar.Callback() {

            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                //see Snackbar.Callback docs for event details
                if (event == Snackbar.Callback.DISMISS_EVENT_TIMEOUT) {
                    // Snackbar closed on its own
                    StarDataSource data = new StarDataSource(getActivity());
                    String bib = app.getLibrary().getIdent();
                    data.remove(item);
                    getActivity().invalidateOptionsMenu();
                    // TODO wenn gefiltert, wird Fragment nicht removed
                }
            }

        });
        snackbar.show();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        if (app.getLibrary() == null) {
            return null;
        }

        if (currentFilterBranchId == 0) {
            return new CursorLoader(getActivity(),
                    app.getStarProviderStarUri(), StarDatabase.COLUMNS,
                    StarDatabase.STAR_WHERE_LIB, new String[]{app
                    .getLibrary().getIdent()}, null);
        } else {
            return new CursorLoader(getActivity(),
                    StarContentProvider.STAR_JOIN_STAR_BRANCH_URI, StarDatabase.COLUMNS,
                    StarDatabase.STAR_WHERE_LIB_BRANCH,
                    new String[]{app.getLibrary().getIdent(), Integer.toString(currentFilterBranchId)},
                    null);
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        adapter.swapCursor(cursor);
        if (cursor.getCount() == 0) {
            tvWelcome.setVisibility(View.VISIBLE);
        } else {
            tvWelcome.setVisibility(View.GONE);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
        adapter.swapCursor(null);
    }

    protected void share() {
        Intent intent = new Intent(android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.addFlags(CompatibilityUtils.getNewDocumentIntentFlag());

        StringBuilder text = new StringBuilder();

        StarDataSource data = new StarDataSource(getActivity());
        List<Starred> items = data.getAllItems(app.getLibrary().getIdent());
        for (Starred item : items) {
            text.append(item.getTitle());
            text.append("\n");
            String shareUrl;
            try {
                shareUrl = app.getApi().getShareUrl(item.getMNr(),
                        item.getTitle());
            } catch (OpacClient.LibraryRemovedException e) {
                return;
            }
            if (shareUrl != null) {
                text.append(shareUrl);
                text.append("\n");
            }
            text.append("\n");
        }

        intent.putExtra(Intent.EXTRA_TEXT, text.toString().trim());
        startActivity(Intent.createChooser(intent,
                getResources().getString(R.string.share)));
    }

    public void exportToStorage() {
        Intent intent = null;
        //android 4.4+; use Storage Access Framework
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            // Create a file with the requested MIME type.
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE,
                    "webopac_starred_" + app.getLibrary().getIdent() + ".json");
            startActivityForResult(intent, REQUEST_CODE_EXPORT);
        } else {        // <android 4.4; share json as text
            intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, getEncodedStarredObjects().toString());
            Intent chooser =
                    Intent.createChooser(intent, getString(R.string.export_starred_to_storage));
            startActivity(chooser);
        }
    }

    /**
     * Mainly to handle exceptions as well as the regular use cases
     */
    private void showExportError() {
        Snackbar.make(getView(), R.string.failed_exporting_file,
                Snackbar.LENGTH_SHORT).show();
    }

    private void showImportError() {
        Snackbar.make(getView(), R.string.failed_importing_file,
                Snackbar.LENGTH_SHORT).show();
    }


    private void showImportErrorNoPickerApp() {
        Snackbar.make(getView(), R.string.failed_importing_file_picker_app,
                Snackbar.LENGTH_SHORT).show();
    }


    private void showImportWrongFormatError() {
        Snackbar.make(getView(), R.string.failed_importing_file_format,
                Snackbar.LENGTH_SHORT).show();
    }

    private JSONObject getEncodedStarredObjects() {
        JSONObject starred = new JSONObject();
        try {
            starred.put(JSON_LIBRARY_NAME, app.getLibrary().getIdent());
            JSONArray items = new JSONArray();
            StarDataSource data = new StarDataSource(getActivity());
            List<Starred> libItems = data.getAllItems(app.getLibrary().getIdent());
            for (Starred libItem : libItems) {
                JSONObject item = new JSONObject();
                item.put(JSON_ITEM_MNR, libItem.getMNr());
                item.put(JSON_ITEM_TITLE, libItem.getTitle());
                item.put(JSON_ITEM_MEDIATYPE, libItem.getMediaType());

                List<String> branches = data.getBranches(libItem.getId());
                if ((branches != null) && (!branches.isEmpty())) {
                    JSONArray branchItems = new JSONArray();
                    for (String branch: branches) {
                        branchItems.put(branch);
                    }
                    item.put(JSON_ITME_BRANCHES, branchItems);
                }

                items.put(item);
            }
            starred.put(JSON_STARRED_LIST, items);
        } catch (JSONException e) {
            showExportError();
        }
        return starred;
    }

    private void importJson(StarDataSource dataSource, String list) throws JSONException {
        JSONObject savedList = new JSONObject(list);
        String bib = savedList.getString(JSON_LIBRARY_NAME);
        //disallow import if from different library than current library
        if (bib != null && !bib.equals(app.getLibrary().getIdent())) {
            Snackbar.make(getView(), R.string.info_different_library,
                    Snackbar.LENGTH_SHORT).show();
            return;
        }
        JSONArray items = savedList.getJSONArray(JSON_STARRED_LIST);
        for (int i = 0; i < items.length(); i++) {
            JSONObject entry = items.getJSONObject(i);
            if (entry.has(JSON_ITEM_MNR) &&
                    !dataSource.isStarred(bib, entry.getString(JSON_ITEM_MNR)) ||
                    !entry.has(JSON_ITEM_MNR) && !dataSource.isStarredTitle(bib,
                            entry.getString(JSON_ITEM_TITLE))) { //disallow dupes

                String mediatype = entry.optString(JSON_ITEM_MEDIATYPE, null);

                if (entry.has(JSON_ITME_BRANCHES)) {
                    List<Copy> copies = new ArrayList<Copy>();
                    JSONArray branchItems = entry.getJSONArray(JSON_ITME_BRANCHES);
                    for (int j = 0; j < branchItems.length(); j++) {
                        String branch = branchItems.getString(j);
                        Copy copy = new Copy();
                        copy.setBranch(branch);
                        copies.add(copy);
                    }
                    dataSource.star(entry.optString(JSON_ITEM_MNR),
                            entry.getString(JSON_ITEM_TITLE), bib,
                            mediatype != null ? SearchResult.MediaType.valueOf(mediatype) :
                                    null, copies);
                } else {
                    dataSource.star(entry.optString(JSON_ITEM_MNR),
                            entry.getString(JSON_ITEM_TITLE), bib,
                            mediatype != null ? SearchResult.MediaType.valueOf(mediatype) :
                                    null);
                }
            }
        }
    }

    public void importFromStorage() {
        //Use SAF
        Intent intent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
        } else {    //let user use a custom picker
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
        }
        try {
            startActivityForResult(intent, REQUEST_CODE_IMPORT);
        } catch (ActivityNotFoundException e) {
            showImportErrorNoPickerApp();//No picker app installed!
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_EXPORT && resultCode == Activity.RESULT_OK) {
            Log.i("StarredFragment", data.toString());
            Uri uri = data.getData();
            try {
                OutputStream os = getActivity().getContentResolver().openOutputStream(uri);
                if (os != null) {
                    JSONObject starred = getEncodedStarredObjects();
                    PrintWriter pw = new PrintWriter(os, true);
                    pw.write(starred.toString());
                    pw.close();
                    os.close();
                } else {
                    showExportError();
                }
            } catch (FileNotFoundException e) {
                showExportError();
            } catch (IOException e) {
                showExportError();
            }
        } else if (requestCode == REQUEST_CODE_IMPORT && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            InputStream is = null;
            try {
                StarDataSource dataSource = new StarDataSource(getActivity());
                is = getActivity().getContentResolver().openInputStream(uri);
                if (is != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    StringBuilder builder = new StringBuilder();
                    String line = "";

                    char[] chars = new char[1];
                    reader.read(chars);
                    if (chars[0] != '{') {
                        throw new WrongFileFormatException();
                    }
                    builder.append(chars);

                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }

                    String list = builder.toString();

                    importJson(dataSource, list);

                    adapter.notifyDataSetChanged();
                    Snackbar.make(getView(), R.string.info_starred_updated,
                            Snackbar.LENGTH_SHORT).show();
                } else {
                    showImportError();
                }
            } catch (JSONException | IOException e) {
                showImportError();
            } catch (WrongFileFormatException e) {
                showImportWrongFormatError();
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            callback = (Callback) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement StarredFragment.Callback");
        }
    }

    @Override
    public void onResume() {
        LoaderManager.getInstance(this).restartLoader(0, null, this);
        super.onResume();
    }

    @Override
    public void onPause() {
        if (getContext() != null) {
            SharedPreferences sp = PreferenceManager
                    .getDefaultSharedPreferences(getContext());
            SharedPreferences.Editor editor = sp.edit();

            if (currentFilterBranchId != 0) {
                editor.putInt(STATE_FILTER_BRANCH, currentFilterBranchId);
            }

            if (activatedPosition != AdapterView.INVALID_POSITION) {
                // Serialize and persist the activated item position.
                editor.putInt(STATE_ACTIVATED_POSITION, activatedPosition);
            }

            editor.apply();
        }

        super.onPause();
    }

    /**
     * Turns on activate-on-click mode. When this mode is on, list items will be given the
     * 'activated' state when touched.
     */
    private void setActivateOnItemClick(boolean activateOnItemClick) {
        // When setting CHOICE_MODE_SINGLE, ListView will automatically
        // give items the 'activated' state when touched.
        listView.setChoiceMode(activateOnItemClick ? AbsListView.CHOICE_MODE_SINGLE
                : AbsListView.CHOICE_MODE_NONE);
    }

    private void setActivatedPosition(int position) {
        if (position == AdapterView.INVALID_POSITION) {
            listView.setItemChecked(activatedPosition, false);
        } else {
            listView.setItemChecked(position, true);
        }

        activatedPosition = position;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (activatedPosition != AdapterView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_ACTIVATED_POSITION, activatedPosition);
        }
        if (currentFilterBranchId > 0) {
            outState.putInt(STATE_FILTER_BRANCH, currentFilterBranchId);
        }
    }

    public interface Callback {
        public void showDetail(String mNr, SearchResult.MediaType mediatype);

        public void removeFragment();
    }

    private class ItemListAdapter extends SimpleCursorAdapter {

        public ItemListAdapter() {
            super(getActivity(), R.layout.listitem_starred, null,
                    new String[]{"bib"}, null, 0);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            Starred item = StarDataSource.cursorToItem(cursor);

            TextView tv = (TextView) view.findViewById(R.id.tvTitle);
            if (item.getTitle() != null) {
                tv.setText(Html.fromHtml(item.getTitle()));
            } else {
                tv.setText("");
            }

            ImageView ivType = (ImageView) view.findViewById(R.id.ivMediaType);
            if (item.getMediaType() != null) {
                ivType.setImageResource(ResultsAdapter.getResourceByMediaType(item.getMediaType()));
            } else {
                ivType.setImageBitmap(null);
            }

            ImageView ivDelete = (ImageView) view.findViewById(R.id.ivDelete);
            ivDelete.setFocusableInTouchMode(false);
            ivDelete.setFocusable(false);
            ivDelete.setTag(item);
            ivDelete.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    Starred item = (Starred) arg0.getTag();
                    remove(item);
                    callback.removeFragment();
                }
            });
        }
    }

    private class WrongFileFormatException extends Exception {
    }
}
