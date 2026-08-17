import os
import torch

def export_silero_vad():
    output_dir = os.path.join("..", "app", "src", "main", "assets")
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, "silero_vad.onnx")

    print("[*] Downloading Silero VAD PyTorch model...")
    model, _ = torch.hub.load(
        repo_or_dir='snakers4/silero-vad',
        model='silero_vad',
        force_reload=False,
        onnx=False
    )
    model.eval()

    batch_size = 1
    dummy_input = torch.randn(batch_size, 512, dtype=torch.float32)
    dummy_state = torch.zeros(2, batch_size, 128, dtype=torch.float32)
    dummy_sr = torch.tensor(16000, dtype=torch.int64)

    print(f"[*] Exporting ONNX model to: {output_path}")
    torch.onnx.export(
        model,
        (dummy_input, dummy_state, dummy_sr),
        output_path,
        export_params=True,
        opset_version=16,
        do_constant_folding=True,
        input_names=['input', 'state', 'sr'],
        output_names=['output', 'state_out'],
        dynamic_axes={
            'input': {0: 'batch_size'},
            'state': {1: 'batch_size'},
            'output': {0: 'batch_size'},
            'state_out': {1: 'batch_size'}
        }
    )
    print("[+] Export completed: silero_vad.onnx")

if __name__ == "__main__":
    export_silero_vad()
  
