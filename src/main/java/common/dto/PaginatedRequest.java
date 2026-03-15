package common.dto;

import java.io.Serializable;

/**
 * Request wrapper for paginated list queries.
 * Phase 14: Efficiency - Pagination support.
 */
public class PaginatedRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private int pageNumber = 1; // 1-indexed
    private int pageSize = 20; // Default page size
    private Object filter; // Optional filter criteria

    public PaginatedRequest() {
    }

    public PaginatedRequest(int pageNumber, int pageSize) {
        this.pageNumber = Math.max(1, pageNumber);
        this.pageSize = Math.max(1, Math.min(100, pageSize)); // Cap at 100
    }

    public PaginatedRequest(int pageNumber, int pageSize, Object filter) {
        this(pageNumber, pageSize);
        this.filter = filter;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = Math.max(1, pageNumber);
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = Math.max(1, Math.min(100, pageSize));
    }

    public Object getFilter() {
        return filter;
    }

    public void setFilter(Object filter) {
        this.filter = filter;
    }

    /**
     * Get SQL OFFSET value.
     */
    public int getOffset() {
        return (pageNumber - 1) * pageSize;
    }

    @Override
    public String toString() {
        return "PaginatedRequest[page=" + pageNumber + ", size=" + pageSize + "]";
    }
}
