package net.hytaledepot.templates.plugin.events;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class EventsDemoService {
  private final Map<String, AtomicLong> actionCounters = new ConcurrentHashMap<>();
  private final Map<String, String> lastActionBySender = new ConcurrentHashMap<>();
  private final Deque<String> recentEvents = new ArrayDeque<>();
  private volatile Path dataDirectory;

  public void initialize(Path dataDirectory) {
    this.dataDirectory = dataDirectory;
    recentEvents.clear();
  }

  public void onHeartbeat(long tick) {
    actionCounters.computeIfAbsent("heartbeat", key -> new AtomicLong()).incrementAndGet();
    if (tick % 120 == 0) {
      appendEvent("heartbeat:" + tick);
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
    String latest = recentEvents.peekLast() == null ? "none" : recentEvents.peekLast();
    return "ops=" + operationCount()
        + ", recentEvents=" + recentEvents.size()
        + ", latest=" + latest
        + ", dataDirectory=" + directory;
  }

  public void shutdown() {
    recentEvents.clear();
  }

  private String handleDomainAction(String sender, String action, long heartbeatTicks) {
    if ("sample".equals(action) || "event-probe".equals(action)) {
      appendEvent("probe:" + sender + ":" + heartbeatTicks);
      return "probe registered, queue=" + recentEvents.size();
    }
    if ("emit-demo".equals(action)) {
      appendEvent("custom:" + sender + ":quest_state_updated");
      return "custom event emitted";
    }
    if ("clear-events".equals(action)) {
      recentEvents.clear();
      return "event queue cleared";
    }
    return null;
  }

  private void appendEvent(String value) {
    recentEvents.addLast(value);
    while (recentEvents.size() > 32) {
      recentEvents.removeFirst();
    }
  }

  private static String normalizeAction(String action) {
    String normalized = String.valueOf(action == null ? "" : action).trim().toLowerCase();
    return normalized.isEmpty() ? "sample" : normalized;
  }
}
