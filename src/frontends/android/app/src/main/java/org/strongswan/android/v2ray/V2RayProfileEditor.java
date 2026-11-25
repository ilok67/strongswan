package org.strongswan.android.v2ray;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputType;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class V2RayProfileEditor
{
    private V2RayProfileEditor()
    {
    }

    public interface Callback
    {
        void onSaved(String oldLink, String newLink, boolean copyMode);
    }

    private static final class Form
    {
        final Context context;
        final LinearLayout layout;

        final Map<String, EditText> fields =
                new LinkedHashMap<String, EditText>();

        final Map<String, CheckBox> checks =
                new LinkedHashMap<String, CheckBox>();

        Form(Context context)
        {
            this.context = context;

            layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);

            int pad = dp(context, 12);
            layout.setPadding(pad, pad, pad, pad);
        }

        EditText field(String key, String title, String value)
        {
            TextView label = new TextView(context);
            label.setText(title);
            label.setPadding(0, dp(context, 8), 0, dp(context, 3));
            layout.addView(label);

            EditText edit = new EditText(context);
            edit.setSingleLine(true);
            edit.setText(value == null ? "" : value);
            edit.setSelectAllOnFocus(false);

            layout.addView(edit, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            fields.put(key, edit);
            return edit;
        }

        void check(String key, String title, boolean checked)
        {
            CheckBox box = new CheckBox(context);
            box.setText(title);
            box.setChecked(checked);
            layout.addView(box);

            checks.put(key, box);
        }

        String get(String key)
        {
            EditText edit = fields.get(key);
            return edit == null ? "" : edit.getText().toString().trim();
        }

        boolean checked(String key)
        {
            CheckBox box = checks.get(key);
            return box != null && box.isChecked();
        }
    }

    public static void show(
            Context context,
            String originalLink,
            boolean copyMode,
            Callback callback)
    {
        final String link = originalLink == null
                ? ""
                : originalLink.trim();

        final JSONObject outbound;

        try
        {
            outbound = V2RayConfigBuilder.parseOutboundForEditor(link);
        }
        catch (Exception e)
        {
            Toast.makeText(
                    context,
                    "Cannot read this profile",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        final String protocol = outbound.optString("type", "")
                .toLowerCase(java.util.Locale.US);

        final Form form = new Form(context);

        String profileName = V2RayConfigBuilder.name(link);

        if (copyMode)
        {
            profileName = "Copy of " + profileName;
        }

        form.field("name", "Profile name", profileName);

        form.field(
                "server",
                "Server / Host",
                outbound.optString("server", "")
        );

        form.field(
                "port",
                "Port",
                String.valueOf(outbound.optInt("server_port", 443))
        );

        if ("vless".equals(protocol) || "vmess".equals(protocol))
        {
            form.field(
                    "uuid",
                    "UUID",
                    outbound.optString("uuid", "")
            );
        }

        if ("trojan".equals(protocol)
                || "shadowsocks".equals(protocol)
                || "hysteria2".equals(protocol))
        {
            form.field(
                    "password",
                    "Password",
                    outbound.optString("password", "")
            );
        }

        if ("shadowsocks".equals(protocol))
        {
            form.field(
                    "method",
                    "Encryption method",
                    outbound.optString("method", "aes-128-gcm")
            );
        }

        if ("vless".equals(protocol))
        {
            form.field(
                    "flow",
                    "Flow",
                    outbound.optString("flow", "")
            );
        }

        if ("vmess".equals(protocol))
        {
            form.field(
                    "alter_id",
                    "Alter ID",
                    String.valueOf(outbound.optInt("alter_id", 0))
            );

            form.field(
                    "security_method",
                    "VMess security",
                    outbound.optString("security", "auto")
            );
        }

        JSONObject tls = outbound.optJSONObject("tls");

        boolean tlsEnabled = tls != null
                && tls.optBoolean("enabled", false);

        boolean realityEnabled = tls != null
                && tls.has("reality");

        boolean insecure = tls != null
                && tls.optBoolean("insecure", false);

        form.check("tls", "Enable TLS", tlsEnabled);

        if ("vless".equals(protocol)
                || "trojan".equals(protocol)
                || "vmess".equals(protocol)
                || "hysteria2".equals(protocol))
        {
            form.field(
                    "sni",
                    "SNI / Server name",
                    tls == null ? "" : tls.optString("server_name", "")
            );

            JSONArray alpnArray = tls == null
                    ? null
                    : tls.optJSONArray("alpn");

            StringBuilder alpnText = new StringBuilder();

            if (alpnArray != null)
            {
                for (int i = 0; i < alpnArray.length(); i++)
                {
                    if (i > 0)
                    {
                        alpnText.append(",");
                    }

                    alpnText.append(alpnArray.optString(i));
                }
            }

            JSONObject utls = tls == null
                    ? null
                    : tls.optJSONObject("utls");

            form.field(
                    "alpn",
                    "ALPN (comma separated)",
                    alpnText.toString()
            );

            form.field(
                    "fingerprint",
                    "TLS fingerprint",
                    utls == null ? "" : utls.optString("fingerprint", "")
            );

            form.check(
                    "insecure",
                    "Allow insecure certificate",
                    insecure
            );
        }

        if ("vless".equals(protocol) || "trojan".equals(protocol))
        {
            JSONObject reality = tls == null
                    ? null
                    : tls.optJSONObject("reality");

            form.check(
                    "reality",
                    "Enable Reality",
                    realityEnabled
            );

            form.field(
                    "public_key",
                    "Reality public key",
                    reality == null
                            ? ""
                            : reality.optString("public_key", "")
            );

            form.field(
                    "short_id",
                    "Reality short ID",
                    reality == null
                            ? ""
                            : reality.optString("short_id", "")
            );
        }

        JSONObject transport = outbound.optJSONObject("transport");

        String network = transport == null
                ? "tcp"
                : transport.optString("type", "tcp");

        form.field("network", "Transport / Network", network);

        String transportHost = "";
        String path = "";
        String serviceName = "";

        if (transport != null)
        {
            JSONArray hosts = transport.optJSONArray("host");

            if (hosts != null && hosts.length() > 0)
            {
                transportHost = hosts.optString(0, "");
            }

            JSONObject headers = transport.optJSONObject("headers");

            if (headers != null)
            {
                transportHost = headers.optString("Host", transportHost);
            }

            path = transport.optString("path", "");
            serviceName = transport.optString("service_name", "");
        }

        form.field(
                "transport_host",
                "Transport host / WS Host",
                transportHost
        );

        form.field(
                "path",
                "Path",
                path
        );

        form.field(
                "service_name",
                "gRPC service name",
                serviceName
        );

        if ("hysteria2".equals(protocol))
        {
            JSONObject obfs = outbound.optJSONObject("obfs");

            form.field(
                    "obfs_type",
                    "Obfuscation type",
                    obfs == null ? "" : obfs.optString("type", "")
            );

            form.field(
                    "obfs_password",
                    "Obfuscation password",
                    obfs == null ? "" : obfs.optString("password", "")
            );
        }

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(form.layout);

        String title = copyMode
                ? "Copy profile"
                : "Edit profile";

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("Save", (dialog, which) ->
                {
                    try
                    {
                        String newLink = buildLink(
                                form,
                                protocol,
                                realityEnabled
                        );

                        // Check that the generated link is supported.
                        V2RayConfigBuilder.build(newLink, "1.10.0");

                        if (callback != null)
                        {
                            callback.onSaved(link, newLink, copyMode);
                        }
                    }
                    catch (Exception e)
                    {
                        Toast.makeText(
                                context,
                                "Invalid profile: " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String buildLink(
            Form f,
            String protocol,
            boolean wasReality) throws Exception
    {
        String name = f.get("name");
        String host = f.get("server");
        String portText = f.get("port");

        if (name.isEmpty())
        {
            throw new IllegalArgumentException("Profile name is required");
        }

        if (host.isEmpty())
        {
            throw new IllegalArgumentException("Server is required");
        }

        int port;

        try
        {
            port = Integer.parseInt(portText);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Port must be a number");
        }

        if (port < 1 || port > 65535)
        {
            throw new IllegalArgumentException("Port must be 1-65535");
        }

        String safeHost = host;

        if (host.contains(":")
                && !host.startsWith("[")
                && !host.endsWith("]"))
        {
            safeHost = "[" + host + "]";
        }

        String encodedName = Uri.encode(name);

        if ("vmess".equals(protocol))
        {
            JSONObject j = new JSONObject();

            j.put("v", "2");
            j.put("ps", name);
            j.put("add", host);
            j.put("port", String.valueOf(port));
            j.put("id", f.get("uuid"));
            j.put("aid", f.get("alter_id").isEmpty()
                    ? "0"
                    : f.get("alter_id"));
            j.put("scy", f.get("security_method").isEmpty()
                    ? "auto"
                    : f.get("security_method"));

            String network = f.get("network");

            if (network.isEmpty())
            {
                network = "tcp";
            }

            j.put("net", network);
            j.put("host", f.get("transport_host"));
            j.put("path", f.get("path"));
            j.put("tls", f.checked("tls") ? "tls" : "");
            j.put("sni", f.get("sni"));
            j.put("alpn", f.get("alpn"));
            j.put("fp", f.get("fingerprint"));
            j.put("allowInsecure", f.checked("insecure"));

            String encoded = Base64.encodeToString(
                    j.toString().getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP
            );

            return "vmess://" + encoded;
        }

        if ("vless".equals(protocol))
        {
            String uuid = f.get("uuid");

            if (uuid.isEmpty())
            {
                throw new IllegalArgumentException("UUID is required");
            }

            String security = "none";

            if (f.checked("reality"))
            {
                security = "reality";
            }
            else if (f.checked("tls"))
            {
                security = "tls";
            }

            StringBuilder result = new StringBuilder();

            result.append("vless://")
                    .append(Uri.encode(uuid))
                    .append("@")
                    .append(safeHost)
                    .append(":")
                    .append(port)
                    .append("?encryption=none")
                    .append("&security=").append(Uri.encode(security));

            addQuery(result, "flow", f.get("flow"));
            addQuery(result, "sni", f.get("sni"));
            addQuery(result, "alpn", f.get("alpn"));
            addQuery(result, "fp", f.get("fingerprint"));

            if (f.checked("insecure"))
            {
                addQuery(result, "allowInsecure", "true");
            }

            if ("reality".equals(security))
            {
                addQuery(result, "pbk", f.get("public_key"));
                addQuery(result, "sid", f.get("short_id"));
            }

            addTransport(result, f);

            result.append("#").append(encodedName);

            return result.toString();
        }

        if ("trojan".equals(protocol))
        {
            String password = f.get("password");

            if (password.isEmpty())
            {
                throw new IllegalArgumentException("Password is required");
            }

            String security = "none";

            if (f.checked("reality"))
            {
                security = "reality";
            }
            else if (f.checked("tls"))
            {
                security = "tls";
            }

            StringBuilder result = new StringBuilder();

            result.append("trojan://")
                    .append(Uri.encode(password))
                    .append("@")
                    .append(safeHost)
                    .append(":")
                    .append(port)
                    .append("?security=").append(Uri.encode(security));

            addQuery(result, "sni", f.get("sni"));
            addQuery(result, "alpn", f.get("alpn"));
            addQuery(result, "fp", f.get("fingerprint"));

            if (f.checked("insecure"))
            {
                addQuery(result, "allowInsecure", "true");
            }

            if ("reality".equals(security))
            {
                addQuery(result, "pbk", f.get("public_key"));
                addQuery(result, "sid", f.get("short_id"));
            }

            addTransport(result, f);

            result.append("#").append(encodedName);

            return result.toString();
        }

        if ("shadowsocks".equals(protocol))
        {
            String method = f.get("method");
            String password = f.get("password");

            if (method.isEmpty() || password.isEmpty())
            {
                throw new IllegalArgumentException(
                        "Encryption method and password are required"
                );
            }

            String credentials = method + ":" + password;

            String encodedCredentials = Base64.encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP
            );

            return "ss://" + encodedCredentials
                    + "@" + safeHost
                    + ":" + port
                    + "#" + encodedName;
        }

        if ("hysteria2".equals(protocol))
        {
            String password = f.get("password");

            if (password.isEmpty())
            {
                throw new IllegalArgumentException("Password is required");
            }

            StringBuilder result = new StringBuilder();

            result.append("hysteria2://")
                    .append(Uri.encode(password))
                    .append("@")
                    .append(safeHost)
                    .append(":")
                    .append(port);

            addQuery(result, "sni", f.get("sni"));

            if (f.checked("insecure"))
            {
                addQuery(result, "insecure", "1");
            }

            addQuery(result, "obfs", f.get("obfs_type"));
            addQuery(result, "obfs-password", f.get("obfs_password"));

            result.append("#").append(encodedName);

            return result.toString();
        }

        throw new IllegalArgumentException(
                "Unsupported protocol: " + protocol
        );
    }

    private static void addTransport(StringBuilder result, Form f)
    {
        String network = f.get("network");

        if (network.isEmpty())
        {
            network = "tcp";
        }

        addQuery(result, "type", network);

        addQuery(result, "host", f.get("transport_host"));

        addQuery(result, "path", f.get("path"));

        addQuery(result, "serviceName", f.get("service_name"));
    }

    private static void addQuery(
            StringBuilder result,
            String key,
            String value)
    {
        if (value == null || value.isEmpty())
        {
            return;
        }

        result.append("&")
                .append(Uri.encode(key))
                .append("=")
                .append(Uri.encode(value));
    }

    private static int dp(Context context, int value)
    {
        return (int) (
                value * context.getResources()
                        .getDisplayMetrics().density + 0.5f
        );
    }
}
