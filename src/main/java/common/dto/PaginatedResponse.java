package common.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Response wrapper for paginated list queries.
 * Phase 14: Efficiency - Pagination support.
 */
public class PaginatedResponse<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<T> items;
    private int totalCount;
    private int totalPages;
    private int currentPage;
    private int pageSize;

    public PaginatedResponse() {
        this.items = Collections.emptyList();
    }

    public PaginatedResponse(List<T> items, int totalCount, int currentPage, int pageSize) {
        this.items = items != null ? items : Collections.emptyList();
        this.totalCount = totalCount;
        this.currentPage = currentPage;
        this.pageSize = pageSize;
        this.totalPages = pageSize > 0 ? (int) Math.ceil((double) totalCount / pageSize) : 0;
    }

    /**
     * Create an empty paginated response.
     */
    public static <T> PaginatedResponse<T> empty() {
        return new PaginatedResponse<>(Collections.emptyList(), 0, 1, 20);
    }

    /**
     * Create response from a full list (pagination in memory).
     */
    public static <T> PaginatedResponse<T> fromList(List<T> allItems, int page, int pageSize) {
        if (allItems == null || allItems.isEmpty()) {
            return empty();
        }

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, allItems.size());

        if (start >= allItems.size()) {
            return new PaginatedResponse<>(Collections.emptyList(), allItems.size(), page, pageSize);
        }

        return new PaginatedResponse<>(allItems.subList(start, end), allItems.size(), page, pageSize);
    }

    // Getters
    public List<T> getItems() {
        return items;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getPageSize() {
        return pageSize;
    }

    // Setters
    public void setItems(List<T> items) {
        this.items = items != null ? items : Collections.emptyList();
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    // Convenience methods
    public boolean hasNextPage() {
        return currentPage < totalPages;
    }

    public boolean hasPreviousPage() {
        return currentPage > 1;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int getItemCount() {
        return items.size();
    }

    @Override
    public String toString() {
        return String.format("PaginatedResponse[page=%d/%d, items=%d, total=%d]",
                currentPage, totalPages, items.size(), totalCount);
    }
}
