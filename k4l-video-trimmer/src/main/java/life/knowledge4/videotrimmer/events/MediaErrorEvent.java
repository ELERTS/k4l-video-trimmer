package life.knowledge4.videotrimmer.events;

public class MediaErrorEvent {
    public final String message;

    public MediaErrorEvent(String errorMessage) {
        this.message = errorMessage;
    }
}
