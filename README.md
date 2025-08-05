
# VoiceChanger Backend – FreeSWITCH Module

## Overview

This project includes a FreeSWITCH module and a simple frontend for testing the real-time voice changer.

---

## Requirements

- FreeSWITCH (installed and compiled with `mod_voicechanger`)
- Java IDE (e.g., IntelliJ IDEA, Eclipse)
- Web browser (for testing the frontend)
- Necessary permissions for FreeSWITCH CLI access

---

## Setup Instructions

### 1. Enable the FreeSWITCH Module

Before running the backend, make sure the custom module (`mod_voicechanger`) is enabled and properly configured in your FreeSWITCH setup.

Refer to the **Documentation PDF** included with this package for detailed steps.

---

### 2. Run the Backend

1. Open the project in your preferred IDE.
2. Build and run the application.

---

### 3. Open the Frontend

After starting the backend:

1. Navigate to:
   ```
   VoiceChanger/src/main/resources/index.html
   ```
2. Open the file in a web browser (double-click or drag into browser).

---

### 4. Grant Necessary Permissions

To allow FreeSWITCH to interact with the backend module:

- Ensure the `fs_cli` (FreeSWITCH CLI) user has the required permissions.
- You may need to run:
  ```bash
  sudo fs_cli
  ```
  or adjust user privileges depending on your system configuration.

---

## Support

If you face any issues, feel free to contact: **Humayun Ahmed**
