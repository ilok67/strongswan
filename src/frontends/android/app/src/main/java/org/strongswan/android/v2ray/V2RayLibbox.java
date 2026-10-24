package org.strongswan.android.v2ray;

import android.content.Context;

import java.io.File;

import io.nekohasekai.libbox.Libbox;
import io.nekohasekai.libbox.SetupOptions;

public final class V2RayLibbox
{
    private static volatile boolean setupDone = false;
    private static final Object lock = new Object();
   public static final Object GATE = new Object();
    private V2RayLibbox()
    {
    }

    public static void setup(Context context) throws Exception
    {
        synchronized (lock)
        {
            if (setupDone)
            {
                return;
            }
            File work = new File(context.getFilesDir(), "singbox");
            work.mkdirs();
            SetupOptions o = new SetupOptions();
            o.setBasePath(context.getFilesDir().getPath());
            o.setWorkingPath(work.getPath());
            o.setTempPath(context.getCacheDir().getPath());
            Libbox.setup(o);
            setupDone = true;
        }
    }
}
