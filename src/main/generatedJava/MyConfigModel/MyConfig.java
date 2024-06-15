package matyfou.zencraftpriceviewer.config;

import blue.endless.jankson.Jankson;
import io.wispforest.owo.config.ConfigWrapper;
import io.wispforest.owo.config.Option;
import io.wispforest.owo.util.Observable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class MyConfig extends ConfigWrapper<matyfou.zencraftpriceviewer.config.MyConfigModel> {

    public final Keys keys = new Keys();

    private final Option<java.lang.Integer> anIntOption = this.optionForKey(this.keys.anIntOption);
    private final Option<java.lang.Boolean> aBooleanToggle = this.optionForKey(this.keys.aBooleanToggle);
    private final Option<matyfou.zencraftpriceviewer.config.MyConfigModel.Choices> anEnumOption = this.optionForKey(this.keys.anEnumOption);

    private MyConfig() {
        super(matyfou.zencraftpriceviewer.config.MyConfigModel.class);
    }

    private MyConfig(Consumer<Jankson.Builder> janksonBuilder) {
        super(matyfou.zencraftpriceviewer.config.MyConfigModel.class, janksonBuilder);
    }

    public static MyConfig createAndLoad() {
        var wrapper = new MyConfig();
        wrapper.load();
        return wrapper;
    }

    public static MyConfig createAndLoad(Consumer<Jankson.Builder> janksonBuilder) {
        var wrapper = new MyConfig(janksonBuilder);
        wrapper.load();
        return wrapper;
    }

    public int anIntOption() {
        return anIntOption.value();
    }

    public void anIntOption(int value) {
        anIntOption.set(value);
    }

    public boolean aBooleanToggle() {
        return aBooleanToggle.value();
    }

    public void aBooleanToggle(boolean value) {
        aBooleanToggle.set(value);
    }

    public matyfou.zencraftpriceviewer.config.MyConfigModel.Choices anEnumOption() {
        return anEnumOption.value();
    }

    public void anEnumOption(matyfou.zencraftpriceviewer.config.MyConfigModel.Choices value) {
        anEnumOption.set(value);
    }


    public static class Keys {
        public final Option.Key anIntOption = new Option.Key("anIntOption");
        public final Option.Key aBooleanToggle = new Option.Key("aBooleanToggle");
        public final Option.Key anEnumOption = new Option.Key("anEnumOption");
    }
}

