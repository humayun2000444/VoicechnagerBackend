// Voice Changer Application JavaScript

class VoiceChanger {
    constructor() {
        this.audioContext = null;
        this.mediaRecorder = null;
        this.recordingChunks = [];
        this.recordingStream = null;
        this.isRecording = false;
        this.recordingStartTime = 0;
        this.recordingTimer = null;
        this.currentAudioFile = null;
        this.originalAudioBlob = null;

        this.init();
    }

    init() {
        this.setupEventListeners();
        this.setupAudioContext();
        this.updateParameterValues();
    }

    setupEventListeners() {
        // Parameter controls
        document.getElementById('shift').addEventListener('input', this.updateParameterValues.bind(this));
        document.getElementById('formant').addEventListener('input', this.updateParameterValues.bind(this));
        document.getElementById('base').addEventListener('input', this.updateParameterValues.bind(this));

        // Preset buttons
        document.querySelectorAll('.preset-btn').forEach(btn => {
            btn.addEventListener('click', this.handlePreset.bind(this));
        });

        // File upload
        const uploadArea = document.getElementById('upload-area');
        const fileInput = document.getElementById('file-input');
        const browseBtn = document.querySelector('.browse-btn');

        browseBtn.addEventListener('click', () => fileInput.click());
        fileInput.addEventListener('change', this.handleFileSelect.bind(this));

        // Drag and drop
        uploadArea.addEventListener('dragover', this.handleDragOver.bind(this));
        uploadArea.addEventListener('dragleave', this.handleDragLeave.bind(this));
        uploadArea.addEventListener('drop', this.handleFileDrop.bind(this));

        // Recording controls
        document.getElementById('start-recording').addEventListener('click', this.startRecording.bind(this));
        document.getElementById('stop-recording').addEventListener('click', this.stopRecording.bind(this));
        document.getElementById('play-original').addEventListener('click', this.playOriginal.bind(this));

        // Processing
        document.getElementById('process-btn').addEventListener('click', this.processAudio.bind(this));

        // Download
        document.getElementById('download-btn').addEventListener('click', this.downloadProcessedAudio.bind(this));
    }

    async setupAudioContext() {
        try {
            this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
        } catch (error) {
            this.showMessage('Audio context not supported in this browser', 'error');
        }
    }

    updateParameterValues() {
        const shift = document.getElementById('shift').value;
        const formant = document.getElementById('formant').value;
        const base = document.getElementById('base').value;

        document.getElementById('shift-value').textContent = parseFloat(shift).toFixed(1);
        document.getElementById('formant-value').textContent = parseFloat(formant).toFixed(1);
        document.getElementById('base-value').textContent = parseInt(base);
    }

    handlePreset(event) {
        const preset = event.target.dataset.preset;
        const presets = {
            'male-to-female': { shift: 10, formant: 2, base: 100 },
            'female-to-male': { shift: -15, formant: -4, base: 300 },
            'robot': { shift: 0, formant: 5, base: 50 },
            'deep': { shift: -15, formant: -3, base: 250 },
            'high-pitch': { shift: 15, formant: 3, base: 80 },
            'reset': { shift: 0, formant: 0, base: 100 }
        };

        if (presets[preset]) {
            document.getElementById('shift').value = presets[preset].shift;
            document.getElementById('formant').value = presets[preset].formant;
            document.getElementById('base').value = presets[preset].base;
            this.updateParameterValues();
        }
    }

    handleDragOver(event) {
        event.preventDefault();
        document.getElementById('upload-area').classList.add('dragover');
    }

    handleDragLeave(event) {
        event.preventDefault();
        document.getElementById('upload-area').classList.remove('dragover');
    }

    handleFileDrop(event) {
        event.preventDefault();
        document.getElementById('upload-area').classList.remove('dragover');

        const files = event.dataTransfer.files;
        if (files.length > 0) {
            this.handleFile(files[0]);
        }
    }

    handleFileSelect(event) {
        const file = event.target.files[0];
        if (file) {
            this.handleFile(file);
        }
    }

    handleFile(file) {
        if (!file.type.startsWith('audio/')) {
            this.showMessage('Please select a valid audio file', 'error');
            return;
        }

        if (file.size > 50 * 1024 * 1024) { // 50MB limit
            this.showMessage('File size exceeds 50MB limit', 'error');
            return;
        }

        this.currentAudioFile = file;
        this.originalAudioBlob = file;

        // Update UI
        const uploadContent = document.querySelector('.upload-content p');
        uploadContent.innerHTML = `✅ ${file.name} (${this.formatFileSize(file.size)})`;

        document.getElementById('process-btn').disabled = false;
        this.showMessage('Audio file loaded successfully', 'success');
    }

    async startRecording() {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({
                audio: {
                    sampleRate: 44100,
                    channelCount: 1,
                    echoCancellation: true,
                    noiseSuppression: true
                }
            });

            this.recordingStream = stream;
            this.mediaRecorder = new MediaRecorder(stream, {
                mimeType: MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : 'audio/mp4'
            });

            this.recordingChunks = [];

            this.mediaRecorder.ondataavailable = (event) => {
                if (event.data.size > 0) {
                    this.recordingChunks.push(event.data);
                }
            };

            this.mediaRecorder.onstop = () => {
                const audioBlob = new Blob(this.recordingChunks, {
                    type: this.mediaRecorder.mimeType
                });
                this.handleRecordingComplete(audioBlob);
            };

            this.mediaRecorder.start(100); // Collect data every 100ms
            this.isRecording = true;
            this.recordingStartTime = Date.now();

            // Update UI
            document.getElementById('start-recording').disabled = true;
            document.getElementById('stop-recording').disabled = false;

            // Start timer
            this.startRecordingTimer();

            this.showMessage('Recording started', 'success');

        } catch (error) {
            this.showMessage('Could not access microphone: ' + error.message, 'error');
        }
    }

    stopRecording() {
        if (this.mediaRecorder && this.isRecording) {
            this.mediaRecorder.stop();
            this.recordingStream.getTracks().forEach(track => track.stop());
            this.isRecording = false;

            // Update UI
            document.getElementById('start-recording').disabled = false;
            document.getElementById('stop-recording').disabled = true;

            // Stop timer
            this.stopRecordingTimer();

            this.showMessage('Recording stopped', 'success');
        }
    }

    startRecordingTimer() {
        this.recordingTimer = setInterval(() => {
            const elapsed = Date.now() - this.recordingStartTime;
            const minutes = Math.floor(elapsed / 60000);
            const seconds = Math.floor((elapsed % 60000) / 1000);
            document.getElementById('recording-time').textContent =
                `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
        }, 1000);
    }

    stopRecordingTimer() {
        if (this.recordingTimer) {
            clearInterval(this.recordingTimer);
            this.recordingTimer = null;
        }
    }

    handleRecordingComplete(audioBlob) {
        this.originalAudioBlob = audioBlob;
        this.currentAudioFile = new File([audioBlob], 'recorded_audio.webm', {
            type: audioBlob.type
        });

        // Enable buttons
        document.getElementById('play-original').disabled = false;
        document.getElementById('process-btn').disabled = false;

        this.showMessage('Recording complete and ready for processing', 'success');
    }

    playOriginal() {
        if (this.originalAudioBlob) {
            const audioUrl = URL.createObjectURL(this.originalAudioBlob);
            const audio = new Audio(audioUrl);
            audio.play().catch(error => {
                this.showMessage('Could not play audio: ' + error.message, 'error');
            });
        }
    }

    async processAudio() {
        if (!this.currentAudioFile) {
            this.showMessage('Please select or record an audio file first', 'error');
            return;
        }

        const processBtn = document.getElementById('process-btn');
        const progressContainer = document.getElementById('progress-container');
        const progressFill = document.getElementById('progress-fill');
        const progressText = document.getElementById('progress-text');

        try {
            // Update UI for processing
            processBtn.disabled = true;
            processBtn.classList.add('processing');
            progressContainer.style.display = 'block';

            // Get current parameters
            const shift = parseFloat(document.getElementById('shift').value);
            const formant = parseFloat(document.getElementById('formant').value);
            const base = parseFloat(document.getElementById('base').value);

            // Prepare form data
            const formData = new FormData();
            formData.append('audio', this.currentAudioFile);
            formData.append('shift', shift.toString());
            formData.append('formant', formant.toString());
            formData.append('base', base.toString());

            // Simulate progress (since we can't get real progress from the server easily)
            let progress = 0;
            const progressInterval = setInterval(() => {
                progress += Math.random() * 15;
                if (progress > 90) progress = 90;
                progressFill.style.width = progress + '%';
                progressText.textContent = `Processing... ${Math.round(progress)}%`;
            }, 200);

            // Make API call
            const response = await fetch('/api/process', {
                method: 'POST',
                body: formData
            });

            // Clear progress interval
            clearInterval(progressInterval);

            if (!response.ok) {
                throw new Error(`Server error: ${response.status}`);
            }

            // Complete progress
            progressFill.style.width = '100%';
            progressText.textContent = 'Processing complete!';

            // Get processed audio
            const audioBlob = await response.blob();
            this.handleProcessedAudio(audioBlob);

        } catch (error) {
            this.showMessage('Processing failed: ' + error.message, 'error');
            console.error('Processing error:', error);
        } finally {
            // Reset UI
            processBtn.disabled = false;
            processBtn.classList.remove('processing');
            setTimeout(() => {
                progressContainer.style.display = 'none';
                progressFill.style.width = '0%';
            }, 2000);
        }
    }

    handleProcessedAudio(audioBlob) {
        // Create audio URL
        const audioUrl = URL.createObjectURL(audioBlob);

        // Show audio player
        const audioElement = document.getElementById('output-audio');
        const placeholder = document.getElementById('output-placeholder');
        const downloadSection = document.getElementById('download-section');

        placeholder.style.display = 'none';
        audioElement.src = audioUrl;
        audioElement.style.display = 'block';
        downloadSection.style.display = 'block';

        // Store for download
        this.processedAudioBlob = audioBlob;

        this.showMessage('Voice transformation complete!', 'success');
    }

    downloadProcessedAudio() {
        if (this.processedAudioBlob) {
            const url = URL.createObjectURL(this.processedAudioBlob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'voice_changed_audio.wav';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);

            this.showMessage('Download started', 'success');
        }
    }

    showMessage(message, type = 'info') {
        const messagesContainer = document.getElementById('status-messages');
        const messageElement = document.createElement('div');
        messageElement.className = `status-message ${type}`;
        messageElement.textContent = message;

        messagesContainer.appendChild(messageElement);

        // Auto remove after 5 seconds
        setTimeout(() => {
            if (messageElement.parentNode) {
                messageElement.parentNode.removeChild(messageElement);
            }
        }, 5000);
    }

    formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }
}

// Initialize the application when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    window.voiceChanger = new VoiceChanger();
});