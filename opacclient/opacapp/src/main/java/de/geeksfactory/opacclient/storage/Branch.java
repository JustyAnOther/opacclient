package de.geeksfactory.opacclient.storage;

/**
 * Branch-data for a stared media-item
 */
public class Branch {
    /**
     * unique db-row-id
     */
    private int id;
    /**
     * Branch name
     */
    private String branch;

    /**
     * count media-items starred in this brach
     */
    private int count;

    /**
     * lastest timestamp this branch was used for filtering
     */
    private int filtertimestamp;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getFiltertimestamp() {
        return filtertimestamp;
    }

    public void setFiltertimestamp(int filtertimestamp) {
        this.filtertimestamp = filtertimestamp;
    }
}
