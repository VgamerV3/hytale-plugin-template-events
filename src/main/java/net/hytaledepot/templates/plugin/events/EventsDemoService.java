package net.hytaledepot.templates.plugin.events;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class EventsDemoService {
  private final Map<String, AtomicLong> actionCounters = new ConcurrentHashMap<>();
  private final Map<String, String> lastActionBySender = new ConcurrentHashMap<>();
  private final Map<String, String> runtimeValues = new ConcurrentHashMap<>();
  private final Map<String, String> domainState = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> numericState = new ConcurrentHashMap<>();

  private volatile Path dataDirectory;

  public void initialize(Path dataDirectory) {
    this.dataDirectory = dataDirectory;
    runtimeValues.put("category", "Events");
    runtimeValues.put("defaultAction", "event-probe");
    runtimeValues.put("initialized", "true");
  }

  public void onHeartbeat(long tick) {
    actionCounters.computeIfAbsent("heartbeat", key -> new AtomicLong()).incrementAndGet();
    if (tick % 120 == 0) {
      runtimeValues.put("lastHeartbeat", String.valueOf(tick));
    }
  }

  public void recordExternalEvent(String key) {
    actionCounters.computeIfAbsent(String.valueOf(key), item -> new AtomicLong()).incrementAndGet();
  }

  public String applyAction(EventsPluginState state, String sender, String action, long heartbeatTicks) {
    String normalizedSender = String.valueOf(sender == null ? "unknown" : sender);
    String normalizedAction = normalizeAction(action);

    actionCounters.computeIfAbsent(normalizedAction, key -> new AtomicLong()).incrementAndGet();
    lastActionBySender.put(normalizedSender, normalizedAction);

    if ("toggle".equals(normalizedAction)) {
      boolean enabled = state.toggleDemoFlag();
      runtimeValues.put("demoFlag", String.valueOf(enabled));
      return "[Events] demoFlag=" + enabled + ", heartbeatTicks=" + heartbeatTicks;
    }

    if ("info".equals(normalizedAction)) {
      return "[Events] " + diagnostics();
    }

    String domainResult = handleDomainAction(normalizedSender, normalizedAction, heartbeatTicks);
    if (domainResult != null) {
      return "[Events] " + domainResult;
    }

    return "[Events] unknown action='" + normalizedAction + "' (try: info, toggle, sample, event-probe, emit-demo, clear-events)";
  }

  public String describeLastAction(String sender) {
    return lastActionBySender.getOrDefault(String.valueOf(sender), "none");
  }

  public long operationCount() {
    long total = 0;
    for (AtomicLong value : actionCounters.values()) {
      total += value.get();
    }
    return total;
  }

  public String diagnostics() {
    String directory = dataDirectory == null ? "unset" : dataDirectory.toString();
    return "ops="
        + operationCount()
        + ", trackedActions="
        + actionCounters.size()
        + ", domainEntries="
        + domainState.size()
        + ", numericEntries="
        + numericState.size()
        + ", dataDirectory="
        + directory;
  }

  public void shutdown() {
    runtimeValues.put("initialized", "false");
  }

  private String handleDomainAction(String sender, String action, long heartbeatTicks) {
    if ("sample".equals(action) || "event-probe".equals(action)) {
      long count = incrementNumber("event:probe", 1);
      domainState.put("event:last", "probe");
      return "probeCount=" + count;
    }
    if ("emit-demo".equals(action)) {
      long count = incrementNumber("event:custom", 1);
      domainState.put("event:last", "custom");
      return "customEventCount=" + count;
    }
    if ("clear-events".equals(action)) {
      setNumber("event:probe", 0);
      setNumber("event:custom", 0);
      domainState.put("event:last", "cleared");
      return "event counters cleared";
    }
    return null;
  }

  private long incrementNumber(String key, long delta) {
    return numericState.computeIfAbsent(key, item -> new AtomicLong()).addAndGet(delta);
  }

  private long number(String key) {
    return numericState.computeIfAbsent(key, item -> new AtomicLong()).get();
  }

  private void setNumber(String key, long value) {
    numericState.computeIfAbsent(key, item -> new AtomicLong()).set(value);
  }

  private static String normalizeAction(String action) {
    String normalized = String.valueOf(action == null ? "" : action).trim().toLowerCase();
    return normalized.isEmpty() ? "sample" : normalized;
  }
}
