package com.sshtools.pretty;

import java.text.MessageFormat;
import java.util.ResourceBundle;

import org.kordamp.ikonli.fontawesome5.FontAwesomeRegular;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import com.sshtools.jajafx.JajaFXAppWindow;
import com.sshtools.jajafx.TitleBar;
import com.sshtools.pretty.PrettyApp.TTYContextImpl;

import javafx.animation.FadeTransition;
import javafx.beans.property.BooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.util.Pair;

public class PrettyAppWindow extends JajaFXAppWindow<PrettyApp> {
	private final static ResourceBundle RESOURCES = ResourceBundle.getBundle(PrettyAppWindow.class.getName());

	private FontIcon bell;
	private Label updateIconLabel;
	private final AppContext ctx;

	public PrettyAppWindow(Stage stage, Node content, PrettyApp app, AppContext ctx) {
		super(stage, content, app);
		
		this.ctx = ctx;

		scene.getRoot().getStyleClass().add("pretty");
		scene.setFill(Color.TRANSPARENT);
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

		var pretty = (PrettyApp) app;
		var muteProperty = pretty.getContainer().getConfiguration().getBooleanProperty(Constants.MUTE_KEY,
				Constants.UI_SECTION);
		muteProperty.addListener((c, o, n) -> updateMuteIcon(muteProperty));

		var title = super.createTitleBar();
		title.maximizeVisibleProperty().setValue(true);

		bell = new FontIcon();
		bell.setIconSize(18);
		bell.setOnMouseClicked(evt -> muteProperty.set(!muteProperty.get()));
		updateMuteIcon(muteProperty);

		var shell = new FontIcon();
		shell.setIconSize(18);
		shell.setOnMouseClicked(evt -> ((TTYContextImpl) content).activeTty().ifPresent(tty -> tty.togglePricli()));
		shell.setIconCode(FontAwesomeSolid.GREATER_THAN);
		
		var newTab = new FontIcon();
		newTab.setIconSize(18);
		newTab.setOnMouseClicked(evt -> {
			((TTYContextImpl) content).newTab(); 
		} );
		newTab.setIconCode(FontAwesomeSolid.PLUS);

		title.addAccessories(bell, shell, newTab);

		doUpdateUpdatesState(title);

		return title;
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
							if(wnd.titleBar() != null)
								((PrettyAppWindow) wnd).removeUpdateIcon(wnd.titleBar());
						}
						

						var updateDialogPane = new UpdateDialog(ctx);
						var dialog = new Dialog<Pair<String, String>>();
						dialog.setDialogPane(updateDialogPane);
						dialog.setTitle(RESOURCES.getString("update"));
						dialog.initOwner(stage());
						var window = updateDialogPane.getScene().getWindow();
						window.setOnCloseRequest(event -> { 
							window.hide();
							updateDialogPane.hidden();
						});
						updateDialogPane.onRemindMeTomorrow(() -> window.hide());
						dialog.showAndWait();
						
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

	private void updateMuteIcon(BooleanProperty muteProperty) {
		if (bell != null) {
			if (muteProperty.get()) {
				bell.setIconCode(FontAwesomeRegular.BELL_SLASH);
			} else {
				bell.setIconCode(FontAwesomeRegular.BELL);
				bell.setIconCode(FontAwesomeRegular.BELL);
			}
		}
	}
}
