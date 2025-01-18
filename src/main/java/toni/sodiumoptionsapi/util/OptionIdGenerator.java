package toni.sodiumoptionsapi.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import toni.sodiumoptionsapi.api.OptionIdentifier;

#if NEO
import net.neoforged.fml.ModList;
#endif

#if FORGE
import net.minecraftforge.fml.ModList;
#endif

#if FABRIC
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModOrigin;
#endif

public class OptionIdGenerator {
    private static final List<String> BLACKLISTED_PREFIXES = List.of(
        "toni.sodiumoptionsapi",
        "net.caffeinemc.mods.sodium",
        "me.jellysquid.mods.sodium",
        "org.embeddedt.embeddium",
        "net.minecraft",
        "net.neoforged"
    );

    private static final List<String> BLACKLISTED_PACKAGES = List.of(
        "flashback",
        "moulberry",
        "dynamic_fps",
        "axiom"
    );

    private static boolean isAllowedClass(String name) {
        for (String packageName : BLACKLISTED_PACKAGES) {
            if (name.contains(packageName)) {
                return false;
            }
        }

        for(String prefix : BLACKLISTED_PREFIXES) {
            if(name.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    private static Stream<Class<?>> streamFromName(String name) {
        try {
            return Stream.of(Class.forName(name));
        } catch(Throwable e) {
            return Stream.empty();
        }
    }

    private static final Map<Path, String> MOD_ID_FROM_URL_CACHE = new HashMap<>();

    static {
        #if FABRIC
        for(var info : FabricLoader.getInstance().getAllMods()) {
            var origin = info.getOrigin();
            if(origin.getKind() == ModOrigin.Kind.PATH) {
                for(Path rootPath : origin.getPaths()) {
                    MOD_ID_FROM_URL_CACHE.put(rootPath, info.getMetadata().getId());
                }
            }
        }
        #else
        for(var info : ModList.get().getModFiles()) {
            if(info.getMods().isEmpty()) {
                continue;
            }
            Path rootPath;
            try {
                rootPath = info.getFile().findResource("/");
            } catch(Throwable e) {
                continue;
            }
            MOD_ID_FROM_URL_CACHE.put(rootPath, info.getMods().get(0).getModId());
        }
        #endif
    }

    public static <T> OptionIdentifier<T> generateId(String path) {
        var modId = StackWalker.getInstance().walk(frameStream -> {
            return frameStream
                    .map(StackWalker.StackFrame::getClassName)
                    .filter(OptionIdGenerator::isAllowedClass)
                    .flatMap(OptionIdGenerator::streamFromName)
                    .map(clz -> {
                        try {
                            var source = clz.getProtectionDomain().getCodeSource();
                            if(source != null) {
                                URL url = source.getLocation();
                                if(url != null) {
                                    return MOD_ID_FROM_URL_CACHE.get(Paths.get(url.toURI()));
                                }
                            }
                        } catch(URISyntaxException ignored) {}
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .findFirst();
        });
        return modId.filter(id -> !id.equals("minecraft")).map(s -> (OptionIdentifier<T>) OptionIdentifier.create(s, path)).orElse(null);
    }
}