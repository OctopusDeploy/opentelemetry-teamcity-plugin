<%@ include file="/include.jsp"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<a href="https://ui.honeycomb.io/${team}/datasets/${dataset}/trace?trace_id=${traceId}&trace_start_ts=${buildStart}&trace_end_ts=${buildEnd}">
    View trace in honeycomb
</a>
