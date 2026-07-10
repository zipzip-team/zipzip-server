package org.zipzip.zipzipserver.domain.sharedgroup.dto.response;

import java.util.List;

public record SharedGroupListResponse(
        List<SharedGroupSummaryResponse> items, String nextCursor, boolean hasNext) {}
