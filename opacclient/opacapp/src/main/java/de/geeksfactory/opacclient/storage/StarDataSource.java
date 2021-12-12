/**
 * Copyright (C) 2013 by Raphael Michel under the MIT license:
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the Software 
 * is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. 
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, 
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, 
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 */
package de.geeksfactory.opacclient.storage;

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.geeksfactory.opacclient.OpacClient;
import de.geeksfactory.opacclient.objects.Copy;
import de.geeksfactory.opacclient.objects.Detail;
import de.geeksfactory.opacclient.objects.SearchResult;

public class StarDataSource {

    private Activity context;

    public StarDataSource(Activity context) {
        this.context = context;
    }

    public static Starred cursorToItem(Cursor cursor) {
        Starred item = new Starred();
        item.setId(cursor.getInt(0));
        item.setMNr(cursor.getString(1));
        item.setTitle(cursor.getString(3));
        try {
            item.setMediaType(
                    cursor.getString(4) != null ?
                            SearchResult.MediaType.valueOf(cursor.getString(4)) :
                            null);
        } catch (IllegalArgumentException e) {
            // Do not crash on invalid media types stored in the database
        }
        return item;
    }

    public long star(String nr, String title, String bib, SearchResult.MediaType mediaType, List<Copy> copies) {

        // each branch only once
        Set<String> branches = new HashSet<>();
        if ((copies != null) && (!copies.isEmpty())) {
            for (Copy copy : copies) {
                String branch = copy.getBranch();
                if (branch == null) {
                    continue;
                }
                if (!branches.contains(branch))
                {
                    branches.add(branch);
                }
            }
        }

        // branch: name --> id
        List<Long> branchIds = new ArrayList<>();
        for (String branch: branches ) {
            long id = getBranchId(bib, branch);
            if (id == 0) {
                id = insertBranch(bib, branch);
            }
            branchIds.add(Long.valueOf(id));
        }

        // star
        long starId = star(nr, title, bib, mediaType);

        // relationship star / branch
        for (Long branchId: branchIds ) {
            insertStarBranch(starId, branchId);
        }

        return starId;
    }

    public long star(String nr, String title, String bib, SearchResult.MediaType mediaType) {
        ContentValues values = new ContentValues();
        values.put("medianr", nr);
        values.put("title", title);
        values.put("bib", bib);
        values.put("mediatype", mediaType != null ? mediaType.toString() : null);
        Uri uri = context.getContentResolver()
               .insert(((OpacClient) context.getApplication()).getStarProviderStarUri(), values);
        return getId(uri);
    }

    public List<Starred> getAllItems(String bib) {
        List<Starred> items = new ArrayList<>();
        String[] selA = {bib};
        Cursor cursor = context
                .getContentResolver()
                .query(((OpacClient) context.getApplication())
                                .getStarProviderStarUri(),
                        StarDatabase.COLUMNS, StarDatabase.STAR_WHERE_LIB,
                        selA, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Starred item = cursorToItem(cursor);
            items.add(item);
            cursor.moveToNext();
        }
        // Make sure to close the cursor
        cursor.close();
        return items;
    }

    public Starred getItemByTitle(String bib, String title) {
        String[] selA = {bib, title};
        Cursor cursor = context
                .getContentResolver()
                .query(((OpacClient) context.getApplication())
                                .getStarProviderStarUri(),
                        StarDatabase.COLUMNS,
                        StarDatabase.STAR_WHERE_TITLE_LIB, selA, null);
        Starred item = null;

        cursor.moveToFirst();
        if (!cursor.isAfterLast()) {
            item = cursorToItem(cursor);
            cursor.moveToNext();
        }
        // Make sure to close the cursor
        cursor.close();
        return item;
    }

    public Starred getItem(String bib, String id) {
        String[] selA = {bib, id};
        Cursor cursor = context
                .getContentResolver()
                .query(((OpacClient) context.getApplication())
                                .getStarProviderStarUri(),
                        StarDatabase.COLUMNS, StarDatabase.STAR_WHERE_NR_LIB,
                        selA, null);
        Starred item = null;

        cursor.moveToFirst();
        if (!cursor.isAfterLast()) {
            item = cursorToItem(cursor);
            cursor.moveToNext();
        }
        // Make sure to close the cursor
        cursor.close();
        return item;
    }

    public Starred getItem(long id) {
        String[] selA = {String.valueOf(id)};
        Cursor cursor = context
                .getContentResolver()
                .query(((OpacClient) context.getApplication())
                                .getStarProviderStarUri(),
                        StarDatabase.COLUMNS, StarDatabase.STAR_WHERE_ID, selA,
                        null);
        Starred item = null;

        cursor.moveToFirst();
        if (!cursor.isAfterLast()) {
            item = cursorToItem(cursor);
            cursor.moveToNext();
        }
        // Make sure to close the cursor
        cursor.close();
        return item;
    }

    public boolean isStarred(String bib, String id) {
        if (id == null) {
            return false;
        }
        String[] selA = {bib, id};
        Cursor cursor = context
                .getContentResolver()
                .query(((OpacClient) context.getApplication())
                                .getStarProviderStarUri(),
                        StarDatabase.COLUMNS, StarDatabase.STAR_WHERE_NR_LIB,
                        selA, null);
        int c = cursor.getCount();
        cursor.close();
        return (c > 0);
    }

    public boolean isStarredTitle(String bib, String title) {
        if (title == null) {
            return false;
        }
        String[] selA = {bib, title};
        Cursor cursor = context
                .getContentResolver()
                .query(((OpacClient) context.getApplication())
                                .getStarProviderStarUri(),
                        StarDatabase.COLUMNS,
                        StarDatabase.STAR_WHERE_TITLE_LIB, selA, null);
        int c = cursor.getCount();
        cursor.close();
        return (c > 0);
    }

    public void remove(Starred item) {
        String[] selA = {"" + item.getId()};
        context.getContentResolver()
               .delete(((OpacClient) context.getApplication())
                               .getStarProviderStarUri(),
                       StarDatabase.STAR_WHERE_ID, selA);
    }

    public void renameLibraries(Map<String, String> map) {
        for (Entry<String, String> entry : map.entrySet()) {
            ContentValues cv = new ContentValues();
            cv.put("bib", entry.getValue());

            context.getContentResolver()
                   .update(((OpacClient) context.getApplication())
                                   .getStarProviderStarUri(),
                           cv, StarDatabase.STAR_WHERE_LIB,
                           new String[]{entry.getKey()});
        }
    }

    /**
     * setzt Column Filtertimestamp zur branchId
     * @param id branchId
     * @param time
     */
    public void updateBranchFiltertimestamp(int id, long time) {
        ContentValues cv = new ContentValues();
        cv.put("filtertimestamp", time);
        context.getContentResolver()
               .update(StarContentProvider.BRANCH_URI,
                       cv, StarDatabase.BRANCH_WHERE_ID,
                       new String[]{Integer.toString(id)});
    }

    /**
     * ermittelt alle Branches (Namen) zu einer starId
     * @param starId
     * @return
     */
    public List<String> getBranches(int starId) {
        String[] proj = {"branch, count(*) as count"};
        String[] selA = { Integer.toString(starId)};
        Cursor cursor = context
                .getContentResolver()
                .query(StarContentProvider.STAR_BRANCH_JOIN_BRANCH_URI, proj,
                        "id_star = ?", selA, null);

        List<String> list = new ArrayList<String>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            list.add(cursor.getString(0));
            cursor.moveToNext();
        }
        // Make sure to close the cursor
        cursor.close();

        return list;
    }


    private int getBranchId(String bib, String branch) {
        if (branch == null) {
            return 0;
        }
        String[] selC = {"id"};
        String[] selA = {bib, branch};
        Cursor cursor = context
                .getContentResolver()
                .query(StarContentProvider.BRANCH_URI,
                        selC,
                        StarDatabase.BRANCH_WHERE_LIB_BRANCH, selA, null);

        int id = 0;
        cursor.moveToFirst();
        if (!cursor.isAfterLast()) {
            id = cursor.getInt(0);
        }
        // Make sure to close the cursor
        cursor.close();

        return id;
    }

    public List<Branch> getStarredBranches(String bib) {
        String[] proj = {"id, branch, filtertimestamp, count(*) as count"};
        String[] selA = {bib};
        Cursor cursor = context
                .getContentResolver()
                .query(StarContentProvider.STAR_BRANCH_JOIN_BRANCH_URI, proj,
                        "bib = ?", selA, "filtertimestamp DESC");

        List<Branch> list = new ArrayList<Branch>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            Branch item = cursorToBranch(cursor);
            if (item.getCount()>0) {
                list.add(item);
            }
            cursor.moveToNext();
        }
        // Make sure to close the cursor
        cursor.close();

        return list;
    }

    private static Branch cursorToBranch(Cursor cursor) {
        Branch item = new Branch();
        item.setId(cursor.getInt(0));
        item.setBranch(cursor.getString(1));
        item.setFiltertimestamp(cursor.getInt(2));
        item.setCount(cursor.getInt(3));
        return item;
    }

    private long insertBranch(String bib, String branch) {
        ContentValues values = new ContentValues();
        values.put("bib", bib);
        values.put("branch", branch);
        Uri uri = context.getContentResolver().insert(StarContentProvider.BRANCH_URI, values);
        return getId(uri);
    }

    private long insertStarBranch(long starId, long branchId) {
        ContentValues values = new ContentValues();
        values.put("id_star", starId);
        values.put("id_branch", branchId);
        Uri uri = context.getContentResolver().insert(StarContentProvider.STAR_BRANCH_URI, values);
        return getId(uri);
    }

    private long getId(Uri uri) {
        long id = Long.parseLong(uri.getLastPathSegment());
        return id;
    }

}
