from pydub import AudioSegment
from pydub.effects import normalize, high_pass_filter
from denoiser import pretrained
from denoiser.dsp import convert_audio
import torchaudio
import torch

# Denoise the audio with AI
def denoise_ai(wav : str):
    noisy, sr = torchaudio.load(wav)
    noisy = convert_audio(noisy, sr, 16000, 1)

    model = pretrained.dns48()

    with torch.no_grad():
        enhanced = model(noisy[None])[0]

    torchaudio.save(wav, enhanced.cpu(), 16000)

# Denoise the audio by removing frequencies < 100Hz
def denoise_manual(wav):
    audio = AudioSegment.from_file(wav, format="wav")

    cleaned = high_pass_filter(audio, cutoff=100)
    cleaned = normalize(cleaned)

    cleaned.export(wav, format="wav")

# A function combining the 2 functions above
def denoise(wav):
    print("Removing low frequencies...")
    denoise_manual(wav)
    print("Applying ai filter...")
    denoise_ai(wav)

if __name__ == "__main__":
    denoise_ai("received_audio.wav")