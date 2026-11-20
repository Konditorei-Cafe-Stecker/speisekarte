package com.stecker.backend.context;

import com.stecker.backend.model.LocalEvent;
import com.stecker.backend.model.WeatherObservation;

import java.util.ArrayList;
import java.util.List;

/** Lets tests in other packages read the committed context files. */
public final class ContextDataTestAccess {

    private ContextDataTestAccess() {
    }

    public static List<WeatherObservation> weather() {
        return ContextDataImporter.readWeather();
    }

    public static List<LocalEvent> events() {
        List<LocalEvent> events = new ArrayList<>(ContextDataImporter.readFootball());
        events.addAll(ContextDataImporter.readCityEvents());
        return events;
    }
}
