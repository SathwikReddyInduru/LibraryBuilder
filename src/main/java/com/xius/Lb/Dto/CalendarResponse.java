package com.xius.Lb.Dto;

public class CalendarResponse {
    private Long calendarId;
    private String calendarName;
    public CalendarResponse(long long1, String string) {
        this.calendarId=long1;
        this.calendarName=string;
    }
    public Long getCalendarId() {
        return calendarId;
    }
    public void setCalendarId(Long calendarId) {
        this.calendarId = calendarId;
    }
    public String getCalendarName() {
        return calendarName;
    }
    public void setCalendarName(String calendarName) {
        this.calendarName = calendarName;
    }
}