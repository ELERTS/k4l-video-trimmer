package life.knowledge4.videotrimmer.events;

public class MediaProgressEvent {
    final float progress;

    public MediaProgressEvent(float progress) {
        this.progress = progress;
    }

    public float getProgress() {
        return progress;
    }
}
