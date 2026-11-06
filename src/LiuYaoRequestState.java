package com.jianyi.yixue.app;

/** UI-independent request identity. No response may escape the question/chart/session it captured. */
final class LiuYaoRequestState {
    private long generation;
    static final class Snapshot {
        final long generation;
        final String recordId, sessionId, subject, chart, historyJson;
        Snapshot(long generation,String recordId,String sessionId,String subject,String chart,String historyJson) {
            this.generation=generation;this.recordId=recordId;this.sessionId=sessionId;
            this.subject=subject;this.chart=chart;this.historyJson=historyJson;
        }
    }
    void invalidate() { generation++; }
    Snapshot capture(String recordId,String sessionId,String subject,String chart,String historyJson) {
        return new Snapshot(++generation,recordId,sessionId,subject,chart,historyJson);
    }
    boolean accepts(Snapshot request,String recordId,String sessionId,String subject,String chart) {
        return request!=null&&request.generation==generation&&request.recordId.equals(recordId)
            &&request.sessionId.equals(sessionId)&&request.subject.equals(subject)&&request.chart.equals(chart);
    }
}
