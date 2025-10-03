adb shell rm -r /data/local/tmp/llm/
adb shell mkdir -p /data/local/tmp/llm/
export MODEL_NAME="Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048"
adb push ${PWD}/${MODEL_NAME}.task /data/local/tmp/llm/${MODEL_NAME}.task
