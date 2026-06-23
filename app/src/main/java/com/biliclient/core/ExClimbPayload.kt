package com.biliclient.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * ExClimbWuzhi 激活 buvid3 的指纹 payload。
 * 权威来源：bili23 common/data/exclimbwuzhi.py（忠实搬运，仅 5062/b8ce/df35 为动态字段）。
 * 输出形如 {"payload":"<inner-json-string>"}。
 */
object ExClimbPayload {

    private val json = Json { encodeDefaults = true }

    fun build(userAgent: String, uuid: String, nowMs: Long = System.currentTimeMillis()): String {
        val inner = buildJsonObject {
            put("3064", 1)
            put("5062", nowMs.toString())
            put("03bf", "https%3A%2F%2Fwww.bilibili.com%2F")
            put("39c8", "333.1007.fp.risk")
            put("34f1", "")
            put("d402", "")
            put("654a", "")
            put("6e7c", "1699x794")
            putJsonObject("3c43") {
                put("2673", 0); put("5766", 32); put("6527", 0); put("7003", 1); put("807e", 1)
                put("b8ce", userAgent)
                put("641c", 0); put("07a4", "zh-CN"); put("1c57", 32); put("0bd0", 20)
                putJsonArray("748e") { add(960); add(1707) }
                putJsonArray("d61f") { add(912); add(1707) }
                put("fc9d", -480); put("6aa9", "Asia/Shanghai"); put("75b8", 1); put("3b21", 1); put("8a1c", 0)
                put("d52f", "not available"); put("adca", "Win32")
                putJsonArray("80c9") {
                    addPlugin("PDF Viewer")
                    addPlugin("Chrome PDF Viewer")
                    addPlugin("Chromium PDF Viewer")
                    addPlugin("Microsoft Edge PDF Viewer")
                    addPlugin("WebKit built-in PDF")
                }
                put("13ab", "EPQAAAAASUVORK5CYII=")
                put("bfe9", "//TgNIfAAAAAZJREFUAwBde+3wgcxEHQAAAABJRU5ErkJggg==")
                putJsonArray("a3c1") { WEBGL.forEach { add(it) } }
                put("6bc5", "Google Inc. (NVIDIA)~ANGLE (NVIDIA, NVIDIA GeForce RTX 4060 Laptop GPU (0x000028E0) Direct3D11 vs_5_0 ps_5_0, D3D11)")
                put("ed31", 0); put("72bd", 0); put("097b", 0)
                putJsonArray("52cd") { add(0); add(0); add(0) }
                putJsonArray("a658") { FONTS.forEach { add(it) } }
                put("d02f", "124.04347527516074")
            }
            put("54ef", FIELD_54EF)
            put("8b94", "")
            put("df35", uuid)
            put("07a4", "zh-CN")
            put("5f45", JsonPrimitive(null as String?))
            put("db46", 0)
        }
        val innerStr = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), inner)
        val outer = buildJsonObject { put("payload", innerStr) }
        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), outer)
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addPlugin(name: String) {
        addJsonArray {
            add(name)
            add("Portable Document Format")
            addJsonArray {
                addJsonArray { add("application/pdf"); add("pdf") }
                addJsonArray { add("text/pdf"); add("pdf") }
            }
        }
    }

    // 54ef 是一个 JSON 字符串（作为字符串值嵌入）
    private const val FIELD_54EF =
        "{\"b_ut\":\"\",\"home_version\":\"V8\",\"in_new_ab\":true,\"ab_version\":{\"for_ai_home_version\":\"V8\",\"in_theme_version\":\"OPEN\",\"enable_web_push\":\"DISABLE\",\"enable_ai_floor_api\":\"ENABLE\",\"enable_shortcut_key\":\"DISABLE\",\"rcmd_timeout_config\":\"550\",\"home_performance_opt\":\"ssr_fetch_opt\",\"infra_projection\":\"OFF\"},\"ab_split_num\":{\"for_ai_home_version\":54,\"in_theme_version\":30,\"enable_web_push\":10,\"enable_ai_floor_api\":137,\"enable_shortcut_key\":54,\"rcmd_timeout_config\":49,\"home_performance_opt\":49,\"infra_projection\":49},\"uniq_page_id\":\"1671272756362\",\"is_modern\":true}"

    private val WEBGL = listOf(
        "extensions:ANGLE_instanced_arrays;EXT_blend_minmax;EXT_clip_control;EXT_color_buffer_half_float;EXT_depth_clamp;EXT_disjoint_timer_query;EXT_float_blend;EXT_frag_depth;EXT_polygon_offset_clamp;EXT_shader_texture_lod;EXT_texture_compression_bptc;EXT_texture_compression_rgtc;EXT_texture_filter_anisotropic;EXT_texture_mirror_clamp_to_edge;EXT_sRGB;KHR_parallel_shader_compile;OES_element_index_uint;OES_fbo_render_mipmap;OES_standard_derivatives;OES_texture_float;OES_texture_float_linear;OES_texture_half_float;OES_texture_half_float_linear;OES_vertex_array_object;WEBGL_blend_func_extended;WEBGL_color_buffer_float;WEBGL_compressed_texture_s3tc;WEBGL_compressed_texture_s3tc_srgb;WEBGL_debug_renderer_info;WEBGL_debug_shaders;WEBGL_depth_texture;WEBGL_draw_buffers;WEBGL_lose_context;WEBGL_multi_draw;WEBGL_polygon_mode",
        "webgl aliased line width range:[1, 1]",
        "webgl aliased point size range:[1, 1024]",
        "webgl alpha bits:8",
        "webgl antialiasing:yes",
        "webgl blue bits:8",
        "webgl depth bits:24",
        "webgl green bits:8",
        "webgl max anisotropy:16",
        "webgl max combined texture image units:32",
        "webgl max cube map texture size:16384",
        "webgl max fragment uniform vectors:1024",
        "webgl max render buffer size:16384",
        "webgl max texture image units:16",
        "webgl max texture size:16384",
        "webgl max varying vectors:30",
        "webgl max vertex attribs:16",
        "webgl max vertex texture image units:16",
        "webgl max vertex uniform vectors:4095",
        "webgl max viewport dims:[32767, 32767]",
        "webgl red bits:8",
        "webgl renderer:WebKit WebGL",
        "webgl shading language version:WebGL GLSL ES 1.0 (OpenGL ES GLSL ES 1.0 Chromium)",
        "webgl stencil bits:0",
        "webgl vendor:WebKit",
        "webgl version:WebGL 1.0 (OpenGL ES 2.0 Chromium)",
        "webgl unmasked vendor:Google Inc. (NVIDIA)",
        "webgl unmasked renderer:ANGLE (NVIDIA, NVIDIA GeForce RTX 4060 Laptop GPU (0x000028E0) Direct3D11 vs_5_0 ps_5_0, D3D11)",
        "webgl vertex shader high float precision:23",
        "webgl vertex shader high float precision rangeMin:127",
        "webgl vertex shader high float precision rangeMax:127",
        "webgl vertex shader medium float precision:23",
        "webgl vertex shader medium float precision rangeMin:127",
        "webgl vertex shader medium float precision rangeMax:127",
        "webgl vertex shader low float precision:23",
        "webgl vertex shader low float precision rangeMin:127",
        "webgl vertex shader low float precision rangeMax:127",
        "webgl fragment shader high float precision:23",
        "webgl fragment shader high float precision rangeMin:127",
        "webgl fragment shader high float precision rangeMax:127",
        "webgl fragment shader medium float precision:23",
        "webgl fragment shader medium float precision rangeMin:127",
        "webgl fragment shader medium float precision rangeMax:127",
        "webgl fragment shader low float precision:23",
        "webgl fragment shader low float precision rangeMin:127",
        "webgl fragment shader low float precision rangeMax:127",
        "webgl vertex shader high int precision:0",
        "webgl vertex shader high int precision rangeMin:31",
        "webgl vertex shader high int precision rangeMax:30",
        "webgl vertex shader medium int precision:0",
        "webgl vertex shader medium int precision rangeMin:31",
        "webgl vertex shader medium int precision rangeMax:30",
        "webgl vertex shader low int precision:0",
        "webgl vertex shader low int precision rangeMin:31",
        "webgl vertex shader low int precision rangeMax:30",
        "webgl fragment shader high int precision:0",
        "webgl fragment shader high int precision rangeMin:31",
        "webgl fragment shader high int precision rangeMax:30",
        "webgl fragment shader medium int precision:0",
        "webgl fragment shader medium int precision rangeMin:31",
        "webgl fragment shader medium int precision rangeMax:30",
        "webgl fragment shader low int precision:0",
        "webgl fragment shader low int precision rangeMin:31",
        "webgl fragment shader low int precision rangeMax:30"
    )

    private val FONTS = listOf(
        "Arial", "Arial Black", "Arial Narrow", "Book Antiqua", "Bookman Old Style", "Calibri",
        "Cambria", "Cambria Math", "Century", "Century Gothic", "Century Schoolbook", "Comic Sans MS",
        "Consolas", "Courier", "Courier New", "Georgia", "Helvetica", "Impact", "Lucida Bright",
        "Lucida Calligraphy", "Lucida Console", "Lucida Fax", "Lucida Handwriting", "Lucida Sans",
        "Lucida Sans Typewriter", "Lucida Sans Unicode", "Microsoft Sans Serif", "Monotype Corsiva",
        "MS Gothic", "MS PGothic", "MS Reference Sans Serif", "MS Sans Serif", "MS Serif",
        "Palatino Linotype", "Segoe Print", "Segoe Script", "Segoe UI", "Segoe UI Light",
        "Segoe UI Semibold", "Segoe UI Symbol", "Tahoma", "Times", "Times New Roman",
        "Trebuchet MS", "Verdana", "Wingdings", "Wingdings 2", "Wingdings 3"
    )
}
