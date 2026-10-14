package de.geeksfactory.opacclient.utils;

import de.geeksfactory.opacclient.R;
import de.geeksfactory.opacclient.frontend.StarredFragment;
import de.geeksfactory.opacclient.objects.SearchResult;
import de.geeksfactory.opacclient.storage.StarDatabase;

public enum EnumFilterMediaType {

    ALL(null, R.id.action_filter_mediatype_all),
    BOOK(SearchResult.MediaType.BOOK, R.id.action_filter_mediatype_book),
    CD(SearchResult.MediaType.CD, R.id.action_filter_mediatype_cd),
    DVD(SearchResult.MediaType.DVD, R.id.action_filter_mediatype_dvd),
    BLUERAY(SearchResult.MediaType.BLURAY, R.id.action_filter_mediatype_blueray);


    final private SearchResult.MediaType mediaType;
    final int menuId;

    EnumFilterMediaType(SearchResult.MediaType mediaType, int menuId) {
        this.mediaType = mediaType;
        this.menuId = menuId;
    }

    public static EnumFilterMediaType fromMenuId(int menuId) {
        for (EnumFilterMediaType value : EnumFilterMediaType.values()) {
            if (value.menuId == menuId) {
                return value;
            }
        }
        return null;
    }

    public SearchResult.MediaType getMediaType() {
        return this.mediaType;
    }
}
