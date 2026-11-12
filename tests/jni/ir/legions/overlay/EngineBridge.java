package ir.legions.overlay;
public final class EngineBridge {
    public native String request(String json);
    public static void main(String[] args) {
        System.load(args[0]);
        var bridge = new EngineBridge();
        var r = bridge.request("{\"op\":\"new\",\"units\":[[\"Warden\",\"Keeper\"],[\"Siren\",\"Titan\"]]}");
        if (!r.contains("\"ok\":true") || !r.contains("\"blue\"")) throw new AssertionError(r);
        var invalid = bridge.request("{}");
        if (!invalid.contains("\"ok\":false")) throw new AssertionError(invalid);
        // Exercise UTF-16/UTF-8 conversion at the actual JNI boundary.
        if (!bridge.request("متن نامعتبر").contains("\"ok\":false")) throw new AssertionError();
        System.out.println("JNI: new state, invalid JSON, Persian strings passed");
    }
}
