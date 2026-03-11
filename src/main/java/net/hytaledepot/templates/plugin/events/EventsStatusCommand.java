package net.hytaledepot.templates.plugin.events;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public final class EventsStatusCommand extends AbstractCommand {
  private final EventsPluginState state;
  private final EventsDemoService demoService;
  private final AtomicLong heartbeatTicks;
  private final LongSupplier uptimeSeconds;
  private final BooleanSupplier heartbeatActive;

  public EventsStatusCommand(
      EventsPluginState state,
      EventsDemoService demoService,
      AtomicLong heartbeatTicks,
      LongSupplier uptimeSeconds,
      BooleanSupplier heartbeatActive) {
    super("hdeventsstatus", "Shows runtime status for the Events template.");
    setAllowsExtraArguments(true);
    this.state = state;
    this.demoService = demoService;
    this.heartbeatTicks = heartbeatTicks;
    this.uptimeSeconds = uptimeSeconds;
    this.heartbeatActive = heartbeatActive;
  }

  @Override
  protected CompletableFuture<Void> execute(CommandContext ctx) {
    state.incrementStatusRequests();
    String sender = String.valueOf(ctx.sender().getDisplayName());

    String line =
        "[Events] lifecycle="
            + state.getLifecycle()
            + ", uptime="
            + uptimeSeconds.getAsLong()
            + "s"
            + ", heartbeatTicks="
            + heartbeatTicks.get()
            + ", heartbeatActive="
            + heartbeatActive.getAsBoolean()
            + ", setupCompleted="
            + state.isSetupCompleted()
            + ", demoFlag="
            + state.isDemoFlagEnabled()
            + ", commands="
            + state.getCommandRequests()
            + ", errors="
            + state.getErrorCount();

    ctx.sendMessage(Message.raw(line));
    ctx.sendMessage(Message.raw("[Events] sender=" + sender + ", lastAction=" + demoService.describeLastAction(sender) + ", " + demoService.diagnostics()));
    return CompletableFuture.completedFuture(null);
  }
}
