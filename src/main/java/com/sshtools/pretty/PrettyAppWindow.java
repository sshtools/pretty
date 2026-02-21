package com.sshtools.pretty;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import org.kordamp.ikonli.fontawesome5.FontAwesomeRegular;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import org.scenicview.ScenicView;

import com.sshtools.jajafx.JajaFXAppWindow;
import com.sshtools.jajafx.TitleBar;
import com.sshtools.jajafx.updateable.UpdateableJajaFXAppWindow;
import com.sshtools.jini.Data.Handle;

import javafx.animation.FadeTransition;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

public class PrettyAppWindow extends UpdateableJajaFXAppWindow<PrettyApp> {
	private final static ResourceBundle RESOURCES = ResourceBundle.getBundle(PrettyAppWindow.class.getName());

	private FontIcon bell;
	private Label updateIconLabel;
	private TTYContext ttyContext;
	private final AppContext ctx;
	private final Handle muteHandle;

	public PrettyAppWindow(Stage stage, PrettyApp app, AppContext ctx) {
		super(stage, app);
		
		this.ctx = ctx;
		
		scene.getRoot().getStyleClass().add("pretty");
		scene.setFill(Color.TRANSPARENT);
		
		muteHandle = ctx.getConfiguration().bindBoolean(this::updateMuteIcon, null, Constants.MUTE_KEY, Constants.UI_SECTION);

	}
	
	public void setContext(TTYContext context) {
		this.ttyContext = context;
		setContent(context.content());
	}
	
	public TTYContext ttyContext() {
		return ttyContext;
	}
	
	public void onClose(WindowEvent evt) {
		super.onClose(evt);
		muteHandle.close();
	}

	public void animateBell() {
		if (bell != null) {
			var ft = new FadeTransition(Duration.millis(250));
			bell.getStyleClass().add("icon-accent");
			ft.setFromValue(1);
			ft.setToValue(0.25f);
			ft.setCycleCount(1);
			ft.setNode(bell);
			ft.setOnFinished(e -> bell.getStyleClass().remove("icon-accent"));
			ft.play();
		}
	}
	
	@Override
	public StageStyle borderlessStageStyle() {
		return StageStyle.TRANSPARENT;
	}

	public void updateUpdatesState() {
		var tb = titleBar();
		if (tb != null) {
			doUpdateUpdatesState(tb);
		}
	}

	@Override
	protected TitleBar createTitleBar() {
		var title = super.createTitleBar();
		title.maximizeVisibleProperty().setValue(true);

		bell = new FontIcon();
		bell.setIconSize(18);
		bell.setOnMouseClicked(evt -> ctx.getConfiguration().ui().put(Constants.MUTE_KEY,
				!ctx.getConfiguration().ui().getBoolean(Constants.MUTE_KEY)));

		var shell = new FontIcon();
		shell.setIconSize(18);
		shell.setOnMouseClicked(evt -> ttyContext.activeTty().ifPresent(tty -> tty.togglePricli()));
		shell.setIconCode(FontAwesomeSolid.GREATER_THAN);
		
		
		var newTab = new FontIcon();
		newTab.setIconSize(18);
		newTab.setOnMouseClicked(evt -> {
			if(evt.getButton() == MouseButton.SECONDARY) {
				evt.consume();
				createShellLaunchMenu().show(newTab, evt.getScreenX(), evt.getScreenY());
			}
			else {
				ttyContext.newTab();
			}
		} );
		newTab.setIconCode(FontAwesomeSolid.PLUS);

		title.addAccessories(bell, shell, newTab);

		doUpdateUpdatesState(title);

		return title;
	}

	protected ContextMenu createShellLaunchMenu() {
		var shellMenu = new ContextMenu();

		var newTabItem = new MenuItem(RESOURCES.getString("newTab"));
		newTabItem.setOnAction((e) -> ttyContext.newTab());
		newTabItem.setAccelerator(KeyCombination.keyCombination("CTRL+SHIFT+T"));

		var newWindow = new MenuItem(RESOURCES.getString("newWindow"));
		newWindow.setOnAction((e) -> ttyContext.newWindow());
		newWindow.setAccelerator(KeyCombination.keyCombination("CTRL+SHIFT+N"));

		shellMenu.getItems().add(newTabItem);
		shellMenu.getItems().add(newWindow);
		shellMenu.getItems().add(new SeparatorMenuItem());
		
		var i = 1;
		for(var shl : ctx.getShells().getAll()) {
			var item = new MenuItem(Shells.toDisplayName(shl));
			if(i < 10) {
				item.setAccelerator(KeyCombination.keyCombination("CTRL+SHIFT+"+ i));
			}
			i++;
			item.setOnAction((e) -> ttyContext.newTab(
				new TTYRequest.Builder().
					withShell(shl).
				build()));
			shellMenu.getItems().add(item);
		}
		return shellMenu;
	}

	protected void doUpdateUpdatesState(TitleBar titleBar) {
		var updateService = app.getContainer().getUpdateService();
		var needsUpdate = updateService.isNeedsUpdating();
		var hasUpdate = updateIconLabel != null;
		if (needsUpdate != hasUpdate) {
			if (needsUpdate) {
					updateIconLabel = new Label();
					var updateIcon = new FontIcon(FontAwesomeSolid.DOWNLOAD);
					var ft = new FadeTransition(Duration.seconds(10));
					updateIcon.getStyleClass().add("icon-accent");
					updateIcon.setIconSize(18);
					updateIcon.setOnMouseClicked(evt -> {
						for (var wnd : app.getWindows()) {
							if(wnd instanceof PrettyAppWindow paw) {
								if(paw.titleBar() != null)
									paw.removeUpdateIcon(wnd.titleBar());
							}
						}
						

						ctx.update(stage());
					});

					ft.setFromValue(1);
					ft.setToValue(0.5f);
					ft.setAutoReverse(true);
					ft.setCycleCount(FadeTransition.INDEFINITE);
					ft.setNode(updateIcon);
					ft.play();

					updateIconLabel.setGraphic(updateIcon);
					updateIconLabel.setTooltip(new Tooltip(MessageFormat.format(RESOURCES.getString("updateAvailable"),
							app.getContainer().getUpdateService().getAvailableVersion())));
					updateIconLabel.setUserData(ft);
					titleBar.addAccessories(updateIconLabel);
			} else {
				removeUpdateIcon(titleBar);
			}
		}
	}

	private void removeUpdateIcon(TitleBar titleBar) {
		if (updateIconLabel != null) {
			var tb = titleBar();
			var ift = (FadeTransition) updateIconLabel.getUserData();
			ift.stop();
			var removeFt = new FadeTransition(Duration.seconds(1));
			removeFt.setFromValue(1);
			removeFt.setToValue(0.05f);
			removeFt.setNode(updateIconLabel);
			removeFt.play();
			removeFt.setOnFinished(evt2 -> {
				tb.removeAccessories(updateIconLabel);
				updateIconLabel = null;
			});
		}
	}

	private void updateMuteIcon(boolean mute) {
		if (bell != null) {
			if (mute) {
				bell.setIconCode(FontAwesomeRegular.BELL_SLASH);
			} else {
				bell.setIconCode(FontAwesomeRegular.BELL);
				bell.setIconCode(FontAwesomeRegular.BELL);
			}
		}
	}
}
