package net.hytaledepot.templates.plugin.events;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class EventsPluginTemplate extends JavaPlugin {
  private final EventsPluginState state = new EventsPluginState();
  private final EventsDemoService demoService = new EventsDemoService();
  private final AtomicLong heartbeatTicks = new AtomicLong();
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "hd-events-worker");
            thread.setDaemon(true);
            return thread;
          });

  private volatile ScheduledFuture<?> heartbeatTask;
  private volatile long startedAtEpochMillis;

  public EventsPluginTemplate(JavaPluginInit init) {
    super(init);
  }

  @Override
  public CompletableFuture<Void> preLoad() {
    state.setLifecycle(EventsPluginLifecycle.PRELOADING);
    getLogger().atInfo().log("[Events] preLoad -> %s", getIdentifier());
    return CompletableFuture.completedFuture(null);
  }

  @Override
  protected void setup() {
    state.setLifecycle(EventsPluginLifecycle.SETTING_UP);
    state.setTemplateName("Events");
    state.setDataDirectory(getDataDirectory().toString());

    demoService.initialize(getDataDirectory());
    state.markSetupCompleted();

    getCommandRegistry().registerCommand(new EventsStatusCommand(state, demoService, heartbeatTicks, this::uptimeSeconds, this::isHeartbeatActive));
    getCommandRegistry().registerCommand(new EventsDemoCommand(state, demoService, heartbeatTicks));

    getEventRegistry().register(PlayerConnectEvent.class, event -> {
      demoService.recordExternalEvent("player_connect");
      demoService.applyAction(state, event.getPlayerRef().getUsername(), "event-probe", heartbeatTicks.get());
    });
    state.setLifecycle(EventsPluginLifecycle.READY);
  }

  @Override
  protected void start() {
    state.setLifecycle(EventsPluginLifecycle.RUNNING);
    startedAtEpochMillis = System.currentTimeMillis();

    heartbeatTask =
        scheduler.scheduleAtFixedRate(
            () -> {
              try {
                long tick = heartbeatTicks.incrementAndGet();
                demoService.onHeartbeat(tick);
                if (tick % 60 == 0) {
                  getLogger().atInfo().log("[Events] heartbeat=%d", tick);
                }
              } catch (Exception exception) {
                state.incrementErrorCount();
                getLogger().atInfo().log("[Events] heartbeat task failed: %s", exception.getMessage());
              }
            },
            1,
            1,
            TimeUnit.SECONDS);

    getTaskRegistry().registerTask(CompletableFuture.completedFuture(null));
  }

  @Override
  protected void shutdown() {
    state.setLifecycle(EventsPluginLifecycle.STOPPING);

    if (heartbeatTask != null) {
      heartbeatTask.cancel(true);
    }

    scheduler.shutdownNow();
    demoService.shutdown();
    state.setLifecycle(EventsPluginLifecycle.STOPPED);
  }

  private long uptimeSeconds() {
    if (startedAtEpochMillis <= 0L) {
      return 0L;
    }
    return Math.max(0L, (System.currentTimeMillis() - startedAtEpochMillis) / 1000L);
  }

  private boolean isHeartbeatActive() {
    return heartbeatTask != null && !heartbeatTask.isCancelled() && !heartbeatTask.isDone();
  }
}
