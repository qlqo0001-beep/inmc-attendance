package com.inmc.attendance.command;

import com.inmc.attendance.AttendancePlugin;
import com.inmc.attendance.attend.Board;
import com.inmc.attendance.util.TextUtil;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** /출석 — 열기 · /출석 관리 · /출석 초기화 <플레이어> <판> · /출석 리로드 */
public final class AttendanceCommand {

    private final AttendancePlugin plugin;

    public AttendanceCommand(AttendancePlugin plugin) { this.plugin = plugin; }

    public void register(JavaPlugin owner) {
        owner.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(tree().build(), "출석", java.util.List.of("attendance", "출석체크"));
        });
    }

    private Player player(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getExecutor() instanceof Player p) return p;
        if (ctx.getSource().getSender() instanceof Player p) return p;
        plugin.messages().send(ctx.getSource().getSender(), "player-only");
        return null;
    }

    private boolean canUse(CommandSourceStack s) {
        if (!(s.getSender() instanceof Player p)) return true;
        return plugin.hasUse(p);
    }

    private boolean canAdmin(CommandSourceStack s) {
        if (!(s.getSender() instanceof Player p)) return true;
        return plugin.hasAdmin(p);
    }

    private LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("출석").requires(this::canUse)
            .executes(ctx -> {
                Player p = player(ctx);
                if (p == null) return 0;
                if (!plugin.hasUse(p)) {
                    plugin.messages().send(p, "no-permission");
                    return 0;
                }
                plugin.gui().open(p, null);
                return 1;
            })
            .then(Commands.literal("관리").requires(this::canAdmin)
                .executes(ctx -> {
                    Player p = player(ctx);
                    if (p == null) return 0;
                    plugin.gui().openAdmin(p);
                    return 1;
                }))
            .then(Commands.literal("리로드").requires(this::canAdmin)
                .executes(ctx -> {
                    plugin.closeMenus();
                    plugin.reloadLocal();
                    plugin.messages().send(ctx.getSource().getSender(), "reloaded",
                            TextUtil.tokens().count(plugin.service().boards().size()));
                    return 1;
                }))
            .then(Commands.literal("초기화").requires(this::canAdmin)
                .then(Commands.argument("플레이어", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (Player p : Bukkit.getOnlinePlayers()) builder.suggest(p.getName());
                        return builder.buildFuture();
                    })
                    .then(Commands.argument("판", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> {
                            for (Board b : plugin.service().boards()) builder.suggest(b.id());
                            return builder.buildFuture();
                        })
                        .executes(ctx -> resetPlayer(ctx,
                                StringArgumentType.getString(ctx, "플레이어"),
                                StringArgumentType.getString(ctx, "판").trim())))));
    }

    private int resetPlayer(CommandContext<CommandSourceStack> ctx, String name, String boardId) {
        var sender = ctx.getSource().getSender();
        Board board = plugin.service().board(boardId);
        if (board == null) {
            plugin.messages().send(sender, "attend-no-board", TextUtil.tokens().value(boardId));
            return 0;
        }
        // 캐시만 본다. 모르는 이름 때문에 메인 스레드가 멈추지 않게.
        OfflinePlayer target = Bukkit.getPlayerExact(name);
        if (target == null) {
            try { target = Bukkit.getOfflinePlayerIfCached(name); }
            catch (Throwable ignored) { target = null; }
        }
        if (target == null) {
            plugin.messages().send(sender, "attend-no-player", TextUtil.tokens().player(name));
            return 0;
        }
        String shown = target.getName() != null ? target.getName() : name;
        plugin.service().resetPlayer(board.id(), target.getUniqueId(), removed -> {
            if (sender instanceof Player p && !p.isOnline()) return;
            plugin.messages().send(sender,
                    removed ? "attend-player-reset" : "attend-player-reset-none",
                    TextUtil.tokens().player(shown).value(board.id()));
        });
        return 1;
    }
}
