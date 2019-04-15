/*
 * MIT License
 *
 * Copyright (c) 2018 creeper123123321 and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.github.creeper123123321.viafabric.mixin.client;

import com.github.creeper123123321.viafabric.providers.VRVersionProvider;
import com.github.creeper123123321.viafabric.util.VersionFormatFilter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Screen;
import net.minecraft.client.gui.menu.MultiplayerScreen;
import net.minecraft.client.gui.menu.YesNoScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.TextComponent;
import net.minecraft.text.TranslatableTextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import us.myles.ViaVersion.api.Via;
import us.myles.ViaVersion.api.protocol.ProtocolRegistry;
import us.myles.ViaVersion.api.protocol.ProtocolVersion;
import us.myles.ViaVersion.protocols.base.VersionProvider;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(MultiplayerScreen.class)
public abstract class MixinMultiplayerScreen extends Screen {
    private TextFieldWidget protocolVersion;
    private ButtonWidget enableClientSideViaVersion;
    private boolean validProtocol;
    private boolean supportedProtocol;

    protected MixinMultiplayerScreen(TextComponent textComponent_1, UnsupportedOperationException e) {
        super(textComponent_1);
        throw e;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        protocolVersion = new TextFieldWidget(font, this.width / 2 + 88, 13, 65, 15);
        protocolVersion.method_1890(new VersionFormatFilter());
        protocolVersion.setChangedListener((text) -> {
            protocolVersion.setSuggestion(null);
            int newVersion = ((VRVersionProvider) Via.getManager().getProviders().get(VersionProvider.class)).clientSideModeVersion;
            validProtocol = true;
            try {
                newVersion = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                ProtocolVersion closest = ProtocolVersion.getClosest(text);
                if (closest != null) {
                    newVersion = closest.getId();
                } else {
                    validProtocol = false;
                    List<String> completions = ProtocolVersion.getProtocols().stream()
                            .map(ProtocolVersion::getName)
                            .flatMap(str -> Stream.concat(
                                    Arrays.stream(str.split("-")),
                                    Arrays.stream(new String[]{str})
                            ))
                            .distinct()
                            .filter(ver -> ver.startsWith(text))
                            .collect(Collectors.toList());
                    if (completions.size() == 1) {
                        protocolVersion.setSuggestion(completions.get(0).substring(text.length()));
                    }
                }
            }
            supportedProtocol = isSupported(newVersion);
            protocolVersion.method_1868(getTextColor()); // Set editable color
            ((VRVersionProvider) Via.getManager().getProviders().get(VersionProvider.class)).clientSideModeVersion = newVersion;
        });
        int clientSideVersion = ((VRVersionProvider) Via.getManager().getProviders().get(VersionProvider.class)).clientSideModeVersion;
        protocolVersion.setText(ProtocolVersion.isRegistered(clientSideVersion)
                ? ProtocolVersion.getProtocol(clientSideVersion).getName()
                : Integer.toString(clientSideVersion));
        this.children.add(protocolVersion);

        enableClientSideViaVersion = new ButtonWidget(this.width / 2 + 48, 13, 105, 15,
                I18n.translate("gui.enable_client_side_button"), button ->
                MinecraftClient.getInstance().openScreen(new YesNoScreen(
                        answer -> {
                            MinecraftClient.getInstance().openScreen(this);
                            if (answer) {
                                try {
                                    FabricLoader.getInstance().getConfigDirectory().toPath().resolve("ViaFabric").resolve("enable_client_side").toFile().createNewFile();
                                    protocolVersion.setVisible(true);
                                    enableClientSideViaVersion.visible = false;
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        },
                        new TranslatableTextComponent("gui.enable_client_side.question"),
                        new TranslatableTextComponent("gui.enable_client_side.warning"),
                        I18n.translate("gui.enable_client_side.enable"),
                        I18n.translate("gui.cancel")
                )));
        protocolVersion.setVisible(FabricLoader.getInstance().getConfigDirectory().toPath().resolve("ViaFabric").resolve("enable_client_side").toFile().exists());
        enableClientSideViaVersion.visible = !protocolVersion.isVisible();
        addButton(enableClientSideViaVersion);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Screen;render(IIF)V"))
    private void onRender(int int_1, int int_2, float float_1, CallbackInfo ci) {
        protocolVersion.render(int_1, int_2, float_1);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        protocolVersion.tick();
    }

    private int getTextColor() {
        if (!validProtocol) {
            return 0xff0000; // Red
        } else if (!supportedProtocol) {
            return 0xFFA500; // Orange
        }
        return 0xE0E0E0; // Default
    }

    private boolean isSupported(int protocol) {
        return ProtocolRegistry.getProtocolPath(ProtocolRegistry.SERVER_PROTOCOL, protocol) != null
                || ProtocolRegistry.SERVER_PROTOCOL == protocol;
    }
}