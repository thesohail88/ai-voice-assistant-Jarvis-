import os
import torch
import torch.nn as nn
import torch.nn.functional as F

class WakeWordKWSNet(nn.Module):
    def __init__(self, input_samples=1280, hidden_dim=64):
        super(WakeWordKWSNet, self).__init__()
        self.conv1 = nn.Conv1d(in_channels=1, out_channels=32, kernel_size=16, stride=4, padding=8)
        self.bn1 = nn.BatchNorm1d(32)
        self.conv2 = nn.Conv1d(in_channels=32, out_channels=64, kernel_size=8, stride=4, padding=4)
        self.bn2 = nn.BatchNorm1d(64)
        self.gru = nn.GRU(input_size=64, hidden_size=hidden_dim, batch_first=True, bidirectional=False)
        self.fc1 = nn.Linear(hidden_dim, 32)
        self.fc2 = nn.Linear(32, 1)

    def forward(self, x):
        if x.dim() == 2:
            x = x.unsqueeze(1)
        x = F.relu(self.bn1(self.conv1(x)))
        x = F.relu(self.bn2(self.conv2(x)))
        x = x.permute(0, 2, 1)
        _, h_n = self.gru(x)
        feat = F.relu(self.fc1(h_n.squeeze(0)))
        probability = torch.sigmoid(self.fc2(feat))
        return probability

def export_wakeword_model(model_name: str, target_filename: str):
    output_dir = os.path.join("..", "app", "src", "main", "assets")
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, target_filename)

    print(f"[*] Initializing neural KWS network for: '{model_name}'...")
    model = WakeWordKWSNet(input_samples=1280, hidden_dim=64)
    model.eval()

    dummy_input = torch.randn(1, 1280, dtype=torch.float32)

    print(f"[*] Exporting ONNX keyword model to: {output_path}")
    torch.onnx.export(
        model,
        dummy_input,
        output_path,
        export_params=True,
        opset_version=16,
        do_constant_folding=True,
        input_names=['input'],
        output_names=['confidence'],
        dynamic_axes={
            'input': {0: 'batch_size'},
            'confidence': {0: 'batch_size'}
        }
    )
    print(f"[+] Successfully exported {target_filename}.")

if __name__ == "__main__":
    export_wakeword_model(model_name="Jarvis", target_filename="jarvis_wakeword.onnx")
    export_wakeword_model(model_name="Friday", target_filename="friday_wakeword.onnx")
  
