#include <jni.h>
#include <android/log.h>
#include <llama.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

#define TATA_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "TataLocalLLM", __VA_ARGS__)

namespace {
std::mutex engine_mutex;
std::atomic<bool> cancel_requested{false};
llama_model * model = nullptr;
llama_context * context = nullptr;
llama_sampler * sampler = nullptr;
bool backend_initialized = false;

void release_locked() {
    if (sampler) {
        llama_sampler_free(sampler);
        sampler = nullptr;
    }
    if (context) {
        llama_free(context);
        context = nullptr;
    }
    if (model) {
        llama_model_free(model);
        model = nullptr;
    }
}

std::string from_java(JNIEnv * env, jstring value) {
    if (!value) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring to_java(JNIEnv * env, const std::string & value) {
    return env->NewStringUTF(value.c_str());
}

std::string make_prompt(const llama_model * loaded_model, const std::string & user_text) {
    const std::string system_text =
        "Kamu adalah Tata AI, asisten digital yang menjawab dalam bahasa Indonesia kecuali diminta lain. "
        "Jawab inti pertanyaan terlebih dahulu dengan bahasa alami, jelas, sopan, dan tidak bertele-tele. "
        "Sesuaikan panjang jawaban: pertanyaan sederhana dijawab singkat; penjelasan atau langkah kerja dijawab terstruktur. "
        "Gunakan riwayat percakapan hanya jika relevan untuk memahami kata seperti itu, tadi, mengapa, atau lanjutkan. "
        "Jangan mengulang jawaban lama tanpa alasan dan jangan menganggap topik lama selalu masih dibahas. "
        "Jangan mengarang fakta, angka, sumber, kutipan, tanggal, atau kemampuan aplikasi. "
        "Bedakan fakta yang diketahui, perkiraan, dan hal yang belum dapat dipastikan; jika tidak tahu, katakan dengan jujur. "
        "Jika pertanyaan ambigu dan jawabannya dapat berubah karena ambiguitas itu, ajukan satu pertanyaan klarifikasi. "
        "Jangan mengaku memiliki perasaan atau pengalaman manusia. "
        "Jangan mengklaim telah mencari internet, membuka aplikasi, menyimpan memori, atau melakukan tindakan yang tidak benar-benar dilakukan. "
        "Jangan menyimpan informasi pengguna sebagai memori atau pengetahuan.";
    const char * tmpl = llama_model_chat_template(loaded_model, nullptr);
    if (tmpl) {
        llama_chat_message messages[] = {
            {"system", system_text.c_str()},
            {"user", user_text.c_str()}
        };
        const int32_t required = llama_chat_apply_template(tmpl, messages, 2, true, nullptr, 0);
        if (required > 0 && required < 24000) {
            std::vector<char> buffer(static_cast<size_t>(required) + 1);
            const int32_t written = llama_chat_apply_template(
                tmpl, messages, 2, true, buffer.data(), static_cast<int32_t>(buffer.size()));
            if (written > 0 && written <= required) {
                return std::string(buffer.data(), static_cast<size_t>(written));
            }
        }
    }
    return "Instruksi sistem: " + system_text + "\n\nPengguna: " + user_text + "\n\nTata AI:";
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_harmanibike_voiceo3_LocalLlmEngine_nativeLoadModel(
    JNIEnv * env, jobject, jstring path_value, jint context_size, jint threads) {
    const std::string path = from_java(env, path_value);
    if (path.empty()) return to_java(env, "Lokasi model kosong.");
    std::lock_guard<std::mutex> guard(engine_mutex);
    cancel_requested.store(false);
    release_locked();
    if (!backend_initialized) {
        llama_backend_init();
        backend_initialized = true;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model = llama_model_load_from_file(path.c_str(), model_params);
    if (!model) {
        TATA_LOGE("Model load failed: %s", path.c_str());
        return to_java(env, "Model gagal dimuat. Pastikan file GGUF lengkap dan arsitekturnya didukung.");
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(std::clamp(context_size, 512, 2048));
    context_params.n_batch = 128;
    context_params.n_ubatch = 128;
    context_params.n_threads = std::clamp(threads, 1, 4);
    context_params.n_threads_batch = std::clamp(threads, 1, 4);
    context = llama_init_from_model(model, context_params);
    if (!context) {
        release_locked();
        return to_java(env, "Konteks model gagal dibuat. Coba model yang lebih kecil.");
    }
    return to_java(env, "OK");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_harmanibike_voiceo3_LocalLlmEngine_nativeGenerate(
    JNIEnv * env, jobject, jstring prompt_value, jint max_tokens) {
    const std::string user_text = from_java(env, prompt_value);
    if (user_text.empty()) return nullptr;
    std::lock_guard<std::mutex> guard(engine_mutex);
    if (!model || !context) return nullptr;
    cancel_requested.store(false);
    llama_memory_clear(llama_get_memory(context), true);

    const std::string prompt = make_prompt(model, user_text);
    const llama_vocab * vocab = llama_model_get_vocab(model);
    int32_t token_count = llama_tokenize(vocab, prompt.c_str(),
        static_cast<int32_t>(prompt.size()), nullptr, 0, true, false);
    if (token_count == INT32_MIN || token_count == 0) return to_java(env, "Prompt tidak dapat diproses.");
    if (token_count < 0) token_count = -token_count;
    std::vector<llama_token> tokens(static_cast<size_t>(token_count));
    token_count = llama_tokenize(vocab, prompt.c_str(),
        static_cast<int32_t>(prompt.size()), tokens.data(), token_count, true, false);
    if (token_count <= 0) return to_java(env, "Tokenisasi pertanyaan gagal.");
    tokens.resize(static_cast<size_t>(token_count));

    const int32_t context_limit = static_cast<int32_t>(llama_n_ctx(context));
    const int32_t output_limit = std::clamp(max_tokens, 16, 256);
    const int32_t prompt_limit = std::max(1, context_limit - output_limit - 8);
    if (static_cast<int32_t>(tokens.size()) > prompt_limit) {
        return to_java(env, "Pertanyaan terlalu panjang untuk konteks model saat ini. Ringkas pertanyaannya lalu coba lagi.");
    }

    for (size_t i = 0; i < tokens.size(); ++i) {
        if (cancel_requested.load()) return to_java(env, "Generasi dihentikan.");
        llama_token token = tokens[i];
        llama_batch batch = llama_batch_get_one(&token, 1);
        if (llama_decode(context, batch) != 0) {
            return to_java(env, "Model gagal memproses prompt. Coba konteks atau model yang lebih kecil.");
        }
    }

    auto params = llama_sampler_chain_default_params();
    sampler = llama_sampler_chain_init(params);
    if (!sampler) return to_java(env, "Sampler AI lokal gagal dibuat.");
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.90f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.70f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string output;
    output.reserve(2048);
    for (int i = 0; i < output_limit; ++i) {
        if (cancel_requested.load()) {
            output += "\n[Generasi dihentikan]";
            break;
        }
        const llama_token token = llama_sampler_sample(sampler, context, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        llama_sampler_accept(sampler, token);
        char piece[256];
        const int32_t piece_size = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
        if (piece_size > 0) {
            output.append(piece, static_cast<size_t>(piece_size));
            if (output.size() >= 12000) break;
        }
        llama_token next_token = token;
        llama_batch batch = llama_batch_get_one(&next_token, 1);
        if (llama_decode(context, batch) != 0) break;
    }

    llama_sampler_free(sampler);
    sampler = nullptr;
    if (output.empty()) return to_java(env, "Model belum menghasilkan jawaban. Coba ulangi pertanyaan.");
    return to_java(env, output);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_harmanibike_voiceo3_LocalLlmEngine_nativeIsLoaded(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> guard(engine_mutex);
    return model != nullptr && context != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_harmanibike_voiceo3_LocalLlmEngine_nativeCancel(JNIEnv *, jobject) {
    cancel_requested.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_harmanibike_voiceo3_LocalLlmEngine_nativeUnload(JNIEnv *, jobject) {
    cancel_requested.store(true);
    std::lock_guard<std::mutex> guard(engine_mutex);
    release_locked();
}
