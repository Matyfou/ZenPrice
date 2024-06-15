package matyfou.zencraftpriceviewer.mixin;

import matyfou.zencraftpriceviewer.zencraftprice.ScreenAccessor;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.text.Text;

@Mixin(Screen.class)
public class ScreenMixin implements ScreenAccessor {

    @Mutable
    @Shadow @Final protected Text title;

    @Override
    public void zencraftprice$setTitle(Text title) {
        this.title = title;
    }
}