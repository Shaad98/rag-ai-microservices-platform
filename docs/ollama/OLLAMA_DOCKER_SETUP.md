# Ollama with Docker

This document explains how Ollama was configured and used inside Docker for local LLM inference, why the initial model download failed, how the Docker networking issue was identified and fixed, and how Ollama storage was reset safely.

---

## 1. Goal

The goal was to run **Ollama entirely inside Docker** and use a local LLM such as:

```text
Qwen3 4B
```

The reason for using Ollama is to run an LLM locally without depending on paid OpenAI/Gemini API usage.

The final architecture is:

```text
Application / RAG Service
          |
          v
   Ollama HTTP API
   localhost:11434
          |
          v
       Qwen3 4B
```

Ollama itself runs inside a Docker container.

---

# 2. Important Concept: Docker Image vs Model

At the beginning, the Ollama Docker image was around several GB in Docker's storage.

That does **not** mean an LLM was already downloaded.

There are two separate things:

```text
Docker Image
    |
    └── ollama/ollama
         Contains the Ollama software

Ollama Volume
    |
    └── /root/.ollama
         Contains downloaded models
```

The Docker image and the downloaded LLM are different.

The Ollama model storage was mounted using:

```bash
-v ollama:/root/.ollama
```

Therefore downloaded models remain in the Docker volume even if the Ollama container itself is removed.

---

# 3. Initial Docker Container

The initial container used:

```text
ollama/ollama:latest
```

Ollama was configured to listen on:

```text
0.0.0.0:11434
```

This allows applications outside the container to communicate with Ollama through:

```text
http://localhost:11434
```

The container also used:

```text
OLLAMA_NO_CLOUD=1
```

This disables Ollama cloud functionality because the intention was to use local models.

---

# 4. First Problem: Qwen3 Pull Failed

The first attempt was:

```bash
docker exec -it ollama ollama pull qwen3:4b
```

The command failed with:

```text
pull model manifest:
dial tcp: lookup registry.ollama.ai on 192.168.1.1:53:
i/o timeout
```

At first this looked like an Ollama problem.

It was actually a **Docker DNS problem**.

---

# 5. Why We Knew It Was Docker DNS

The host machine could resolve the Ollama registry:

```bash
getent hosts registry.ollama.ai
```

and:

```bash
ping -c 3 registry.ollama.ai
```

worked.

However, inside the Docker network the DNS server was:

```text
192.168.1.1
```

and DNS resolution timed out.

This meant:

```text
Host
  |
  └── DNS works

Docker Container
  |
  └── DNS fails
```

So the problem was not that the Ollama registry was unavailable.

---

# 6. Testing Docker DNS

A temporary BusyBox container was used to verify external DNS:

```bash
docker run --rm busybox nslookup registry.ollama.ai 8.8.8.8
```

and:

```bash
docker run --rm busybox nslookup registry.ollama.ai 1.1.1.1
```

Both worked.

This confirmed that Docker containers could communicate with the internet when using working DNS servers.

---

# 7. Fixing Docker DNS

Docker was configured to use:

```text
8.8.8.8
1.1.1.1
```

The Docker daemon configuration was:

```json
{
  "dns": ["8.8.8.8", "1.1.1.1"]
}
```

This was placed in:

```text
/etc/docker/daemon.json
```

After changing Docker's DNS configuration, Docker was restarted.

---

# 8. Verifying the DNS Fix

Inside the Ollama container:

```bash
docker exec ollama cat /etc/resolv.conf
```

showed:

```text
nameserver 8.8.8.8
nameserver 1.1.1.1
```

Then:

```bash
docker exec ollama getent hosts registry.ollama.ai
```

successfully resolved the registry.

Therefore:

```text
Docker DNS problem = FIXED
```

We should not modify `/etc/resolv.conf` on the host because the host was using `systemd-resolved`.

---

# 9. Second Problem: `ssh: no key found`

After fixing DNS, the error changed.

The command:

```bash
docker exec -it ollama ollama pull qwen3:4b
```

now reached the registry but failed with:

```text
pulling manifest
Error: pull model manifest: ssh: no key found
```

This was important because the error was no longer:

```text
DNS timeout
```

The registry was being reached successfully.

---

# 10. Testing the Registry Directly

To verify that the Ollama registry itself was reachable, a BusyBox container was run using the same network namespace as Ollama:

```bash
docker run --rm \
  --network container:ollama \
  busybox \
  wget -S -O - \
  https://registry.ollama.ai/v2/library/qwen3/manifests/4b
```

The registry returned:

```text
HTTP/1.1 200 OK
```

and returned a valid model manifest.

The manifest contained a model layer of approximately:

```text
2.5 GB
```

This confirmed:

```text
DNS                  ✅
Internet             ✅
Registry connection  ✅
Qwen3 manifest       ✅
```

but:

```text
Ollama pull           ❌
```

was still failing during its own manifest handling.

---

# 11. Checking Ollama Cloud Configuration

We checked the environment:

```bash
docker exec ollama env | grep -Ei 'proxy|registry|ssh|ollama'
```

The important values were:

```text
OLLAMA_HOST=0.0.0.0:11434
OLLAMA_NO_CLOUD=1
```

We also checked the container configuration:

```bash
docker inspect ollama --format '{{range .Config.Env}}{{println .}}{{end}}'
```

There was no configured SSH key or proxy-related environment variable causing the problem.

---

# 12. Disabling Ollama Cloud

To make the setup local-only, the container was recreated with:

```bash
-e OLLAMA_NO_CLOUD=1
```

Example:

```bash
docker run -d \
  --name ollama \
  -p 11434:11434 \
  -e OLLAMA_HOST=0.0.0.0:11434 \
  -e OLLAMA_NO_CLOUD=1 \
  -v ollama:/root/.ollama \
  ollama/ollama:0.33.3
```

The logs confirmed:

```text
Ollama cloud disabled: true
```

However, the same:

```text
ssh: no key found
```

error was still observed during `ollama pull`.

Therefore cloud mode was not the root cause of the pull problem.

---

# 13. Testing Different Ollama Versions

Because the error appeared suspiciously like a version-specific issue, several Ollama versions were tested.

Versions tested included:

```text
0.34.1
0.34.0
0.33.3
```

The same error appeared:

```text
pull model manifest: ssh: no key found
```

This made a simple version-specific explanation less likely.

---

# 14. Why the Volume Became a Suspect

The same Docker volume had been reused while changing Ollama versions.

The volume was:

```text
ollama
```

mounted to:

```text
/root/.ollama
```

The container had gone through:

```text
0.34.1
   ↓
0.34.0
   ↓
0.33.3
```

while continuing to use the same volume.

Because no important model had been downloaded yet, the volume could safely be reset as a clean test.

Before deletion, Ollama reported:

```text
total blobs: 0
models=0
```

So there was no valuable model data to preserve.

---

# 15. Resetting the Ollama Volume

The container was stopped and removed:

```bash
docker stop ollama
docker rm ollama
```

Then the Ollama volume was removed:

```bash
docker volume rm ollama
```

The volume list was checked:

```bash
docker volume ls | grep ollama
```

Afterward, a fresh volume was created:

```bash
docker volume create ollama
```

This does not delete unrelated Docker volumes.

It only removes the specific:

```text
ollama
```

volume.

---

# 16. Recreating Ollama

The clean Ollama container can be started with:

```bash
docker run -d \
  --name ollama \
  -p 11434:11434 \
  -e OLLAMA_HOST=0.0.0.0:11434 \
  -e OLLAMA_NO_CLOUD=1 \
  -v ollama:/root/.ollama \
  ollama/ollama:0.33.3
```

Check the container:

```bash
docker ps
```

Check Ollama logs:

```bash
docker logs ollama --tail 20
```

Check installed models:

```bash
docker exec ollama ollama list
```

Since the volume was freshly created, the model list should initially be empty.

---

# 17. Downloading Qwen3 4B

The model can be downloaded with:

```bash
docker exec -it ollama ollama pull qwen3:4b
```

Qwen3 4B's model layer is approximately:

```text
2.5 GB
```

Therefore enough disk space should be available before downloading it.

The downloaded model is stored inside the Docker volume:

```text
ollama
   ↓
/root/.ollama/models
```

It is **not** stored directly in the user's home directory.

---

# 18. Checking the Model

After downloading:

```bash
docker exec ollama ollama list
```

Example:

```text
NAME
qwen3:4b
```

The exact size shown by Ollama may vary depending on the model representation and metadata.

---

# 19. Running Qwen3

Once installed, start an interactive chat:

```bash
docker exec -it ollama ollama run qwen3:4b
```

Ollama will provide a prompt similar to:

```text
>>>
```

Now type:

```text
Explain Kubernetes Service in simple words.
```

Ollama sends the prompt to Qwen3 and returns the generated response.

To exit the interactive session:

```text
/bye
```

---

# 20. Using Ollama Through HTTP

Ollama also exposes an HTTP API.

Because the Docker port is mapped:

```bash
-p 11434:11434
```

the host can access:

```text
http://localhost:11434
```

For example:

```bash
curl http://localhost:11434/api/generate \
  -d '{
    "model": "qwen3:4b",
    "prompt": "Explain Docker volumes in simple words.",
    "stream": false
  }'
```

The response is returned as JSON.

This is the interface that applications such as a RAG backend can use.

---

# 21. How This Fits Into ShaadRAG

Ollama is not the entire RAG system.

It is the **LLM inference layer**.

A simplified ShaadRAG flow is:

```text
User
  |
  v
Frontend
  |
  v
API Gateway
  |
  v
RAG Orchestrator
  |
  +----------------------+
  |                      |
  v                      v
Retrieval Service     Other Services
  |
  v
Relevant document chunks
  |
  v
Prompt construction
  |
  v
Ollama
  |
  v
Qwen3 4B
  |
  v
Generated answer
  |
  v
User
```

For example:

```text
User:
"What is Kubernetes Service?"

        ↓

Retriever:
Find relevant document chunks

        ↓

RAG Orchestrator:
Build prompt containing the chunks

        ↓

Ollama:
Generate answer using Qwen3 4B

        ↓

Frontend:
Display answer
```

---

# 22. Important Docker Commands

### Check Ollama container

```bash
docker ps
```

### Check Ollama logs

```bash
docker logs ollama
```

### Check installed models

```bash
docker exec ollama ollama list
```

### Open Ollama CLI

```bash
docker exec -it ollama ollama
```

### Run a model

```bash
docker exec -it ollama ollama run qwen3:4b
```

### Check volume

```bash
docker volume inspect ollama
```

### Check Ollama container configuration

```bash
docker inspect ollama
```

---

# 23. What Was Actually Fixed

The first confirmed problem was:

```text
Docker container DNS resolution
```

It was fixed by configuring Docker to use:

```text
8.8.8.8
1.1.1.1
```

After that, the registry became reachable.

The second problem encountered was:

```text
ssh: no key found
```

This occurred during Ollama's model manifest handling even though the registry itself was reachable.

Several checks were performed:

```text
DNS                  ✅
Registry reachable   ✅
Manifest available   ✅
Cloud disabled       ✅
Multiple versions    ❌ same error
```

A fresh Ollama volume was then used because the previous volume had been reused across multiple Ollama versions and there were no important models inside it.

The clean volume provides a fresh Ollama model-storage state.

---

# 24. What NOT to Delete

Do not blindly run:

```bash
docker system prune --volumes
```

when the intention is only to clean Ollama.

That command can remove unused Docker volumes belonging to other projects.

Instead, when specifically resetting Ollama, target only:

```bash
docker volume rm ollama
```

And only do this when there are no Ollama models/data that need to be preserved.

---

# 25. Storage Structure

Conceptually:

```text
Docker
│
├── ollama/ollama image
│     └── Ollama application
│
└── ollama volume
      └── /root/.ollama
            └── models
                 ├── blobs
                 └── manifests
```

The important separation is:

```text
Container = Ollama application
Volume    = Ollama model data
```

Therefore deleting and recreating the container normally does not delete downloaded models as long as the same volume is kept.

---

# 26. Why Qwen3 4B Was Chosen

Qwen3 4B was selected as a practical starting model for the local environment.

The machine has limited RAM compared with larger LLMs, so starting with a 4B-class model avoids immediately jumping to much larger models.

Inference is expected to use CPU in this environment rather than a dedicated GPU.

---

# 27. Current Mental Model

The main things to remember are:

```text
Docker
  → runs Ollama

Ollama
  → manages local LLMs

Qwen3 4B
  → the actual LLM

Ollama Volume
  → stores the downloaded model

Port 11434
  → Ollama HTTP API

RAG
  → retrieves context and sends it to the LLM
```

The most important command for starting a chat is:

```bash
docker exec -it ollama ollama run qwen3:4b
```

And the most important command for applications is:

```text
http://localhost:11434
```

---

# 28. Summary

The setup went through these stages:

```text
Install Ollama Docker image
        ↓
Create persistent Ollama volume
        ↓
Try Qwen3 4B
        ↓
DNS timeout
        ↓
Diagnose Docker DNS
        ↓
Configure Docker DNS
        ↓
Registry becomes reachable
        ↓
New error:
"ssh: no key found"
        ↓
Check cloud settings
        ↓
Test multiple Ollama versions
        ↓
Suspect reused Ollama volume
        ↓
Remove old Ollama volume
        ↓
Create fresh Ollama volume
        ↓
Run clean Ollama container
        ↓
Pull/run Qwen3 4B
```

The key lesson is that **the first failure was definitely Docker DNS**, while the later `ssh: no key found` error was a separate Ollama-side problem encountered during model pulling.

---

## Useful Commands — Quick Reference

```bash
# Check container
docker ps

# Logs
docker logs ollama --tail 50

# List models
docker exec ollama ollama list

# Pull model
docker exec -it ollama ollama pull qwen3:4b

# Run model
docker exec -it ollama ollama run qwen3:4b

# Ollama API
curl http://localhost:11434/api/generate

# Inspect volume
docker volume inspect ollama

# Remove only Ollama volume
docker volume rm ollama
```
