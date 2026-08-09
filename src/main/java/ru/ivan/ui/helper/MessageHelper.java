package ru.ivan.ui.helper;

import javafx.animation.PauseTransition;
import javafx.event.Event;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

/**
 * Utility class for displaying temporary user notifications and debug information.
 * Supports visual tooltips for UI nodes and fallback alerts for generic events.
 */
public class MessageHelper {

    private static int deltaY;

    /**
     * Displays a debug notification. If the event source is a UI Node, a temporary tooltip
     * is shown near the component. Otherwise, a modal alert dialog is displayed.
     * The original event is consumed after the notification is triggered.
     *
     * @param event   The event triggering the debug message.
     * @param message The text content to display.
     */
    public static void debug(Event event, String message) {
        //STUB FOR Debug - you must replace all calls
        if (event.getSource() instanceof Node node) {
            deltaY += 42;
            Point2D pos = node.localToScreen(0, deltaY);
            message = message + "\nid:" + node.getId();
            Tooltip tooltip = new Tooltip(message);
            tooltip.show(node.getScene().getWindow(), pos.getX(), pos.getY());
            PauseTransition pause = new PauseTransition(Duration.seconds(1));
            pause.setOnFinished(e -> {
                tooltip.hide();
                deltaY -= 42;
            });
            pause.play();
        } else {
            message = message + "\n" + event.getSource();
            new Alert(Alert.AlertType.NONE, message, ButtonType.OK).show();
        }
        event.consume();
    }
}
