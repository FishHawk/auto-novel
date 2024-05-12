MODEL=sakura-14b-qwen2beta-v0.9-iq4_xs_ver2
# MODEL=sakura-32b-qwen2beta-v0.9-iq4xs
llama.cpp/server -m ${MODEL}.gguf -c 4096 -ngl 999 -a ${MODEL} --port 6006