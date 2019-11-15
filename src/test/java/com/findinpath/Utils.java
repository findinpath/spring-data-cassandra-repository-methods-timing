package com.findinpath;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class Utils {
  private Utils(){}

  public static Timer getExactlyOneTimer(List<Meter> meters, String metricId, Tag... tags) {
    var timers = getTimers(meters, metricId, tags);
    if (timers.size() == 0) {
      throw new IllegalArgumentException("No meters found for the specified parameters");
    }
    if (timers.size() != 1) {
      throw new IllegalArgumentException("More than one meter ("+timers.size()+") retrieved for the specified parameters");
    }

    return timers.get(0);
  }

  public static List<Timer> getTimers(List<Meter> meters, String metricId, Tag... tags) {
    return meters
        .stream()
        .filter(meter -> matches(meter, metricId, tags))
        .filter(meter -> meter instanceof Timer)
        .map(meter -> (Timer) meter)
        .collect(Collectors.toList());
  }

  private static  boolean matches(Meter meter, String metricId, Tag... tags) {
    return Optional.of(meter)
        .filter(m -> m.getId().getName().equals(metricId))
        .filter(m -> matches(m, tags))
        .isPresent();

  }

  private static boolean matches(Meter meter, Tag... tags) {
    return Optional.ofNullable(tags).stream().flatMap(Arrays::stream)
        .filter(tag -> !tag.getValue().equals(meter.getId().getTag(tag.getKey())))
        .findAny()
        .isEmpty();
  }
}
