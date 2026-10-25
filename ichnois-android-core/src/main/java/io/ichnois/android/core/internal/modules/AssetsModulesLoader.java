package io.ichnois.android.core.internal.modules;

import android.content.Context;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.android.core.ContextUtils;
import io.ichnois.internal.modules.ModulesLoader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class AssetsModulesLoader extends ModulesLoader {

  private final @NotNull Context context;

  public AssetsModulesLoader(final @NotNull Context context, final @NotNull IchnoisCoreOptions options) {
    super(options.getLogger());
    this.context = ContextUtils.getApplicationContext(context);

    // pre-load modules on a bg thread to avoid doing so on the main thread in case of a crash/error
    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                getOrLoadModules();
              });
    } catch (Throwable e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "AssetsModulesLoader submit failed", e);
    }
  }

  @Override
  protected Map<String, String> loadModules() {
    final Map<String, String> modules = new TreeMap<>();

    try (final InputStream stream = context.getAssets().open(EXTERNAL_MODULES_FILENAME)) {
      return parseStream(stream);
    } catch (FileNotFoundException e) {
      logger.log(IchnoisCoreLevel.INFO, "%s file was not found.", EXTERNAL_MODULES_FILENAME);
    } catch (IOException e) {
      logger.log(IchnoisCoreLevel.ERROR, "Error extracting modules.", e);
    }
    return modules;
  }
}
