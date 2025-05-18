import socket
import os
import wave
import time
from denoise import denoise

# Configure socket
server = socket.socket()
server.bind(('0.0.0.0', 1337))
server.listen(1)
print("Waiting for audio...")

# Convert pcm to wav
def pcm_to_wav(pcm_file, wav_file, sample_rate=16000, channels=1):
    with open(pcm_file, 'rb') as pcm:
        pcm_data = pcm.read()

    with wave.open(wav_file, 'wb') as wav:
        wav.setnchannels(channels)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        wav.writeframes(pcm_data)

# Receiver
def receive_audio():
    conn, addr = server.accept()
    print(f"Connection received from {addr}...")

    rcv_time = time.time()
    pcm_file = f"recording_{rcv_time}.pcm"
    with open(pcm_file, "wb") as f:
        while True:
            data = conn.recv(4096)
            if not data:
                print("Audio received, closing connection.")
                conn.close()
                break
            f.write(data)

    wav_file = pcm_file.replace(".pcm", ".wav")
    pcm_to_wav(pcm_file, wav_file)
    print(f"Audio saved and converted to {wav_file}")

    os.remove(pcm_file)

    return wav_file

# The best main you've ever seen
if __name__ == "__main__":
    while True:
        try:
            wav_file = receive_audio()

            # See if it was an audio or just a verification
            if os.path.exists(wav_file):
                print("Denoising...")
                denoise(wav_file)
                print("Denoising complete")

        except Exception as e:
            print(f"Error: {e}")