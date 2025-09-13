# Disclosure

This is based on Stephen Bradshaw's [Burp Python Gateway Burp Plugin](https://github.com/stephenbradshaw/BurpPythonGateway), but adapted to support Burp Suite's new Montoya API (2025.08). 

# What can I use this for?

This extension exposes Burp Suite's API over the network for Python. From your Python script, you can easily access everything that is accessible to Burp Suite extensions without actually writing an extension. How easily? Here is an example of iterating over the proxy history.

```
from py4j.java_gateway import JavaGateway

gateway = JavaGateway()
burp_api = gateway.entry_point
for entry in burp_api.proxy().history():
    print(entry.url())
```

Check out [Burp Suite's Montoya API](https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html) for ideas. The above example uses the [Proxy interface](https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/proxy/Proxy.html). It is also a good idea to get familiar with how [Py4j](https://www.py4j.org/) works.

# How to setup?
1. Load this extension into Burp Suite. See [Releases](https://github.com/PaperTsar/BurpPythonGatewayMontoya/releases) for pre-built JAR  or build them yourself as described at the end.
2. Install [py4j](https://www.py4j.org/) in your Python installation 
```python
    pip install py4j
```

## Can Java call Python back? Heck Yes!
Here is an example of registering a proxy callback. It will be called every time an HTTP response comes back to Burp.

```python
from py4j.java_gateway import JavaGateway

class ProxyResponseHandler:
    def __init__(self, gateway):
        self.gateway = gateway

    def handleResponseToBeSent(self, response):
        print(f"Status code: {response.statusCode()} -> {response.request().url()}")
        return self.gateway.jvm.burp.api.montoya.proxy.http.ProxyResponseToBeSentAction.continueWith(response)

    def handleResponseReceived(self, response):
        return self.gateway.jvm.burp.api.montoya.proxy.http.ProxyResponseReceivedAction.continueWith(response)

    class Java:
        implements = ["burp.api.montoya.proxy.http.ProxyResponseHandler"]

registration = None

try:
    gateway = JavaGateway()
    burp_api = gateway.entry_point

    # Register our callback with Burp
    registration = burp_api.proxy().registerResponseHandler(ProxyResponseHandler(gateway))
    input() # keep main thread alive and blocked
finally:
    if registration is not None:
        registration.deregister() # Clean up
```

# Running Burp and Python on a different host
1. Configure the extension settings in `Burp > Settings > Extentions > Burp Python Gateway Monotya`.
2. Reload the extension.
3. Connect in Python:
```python
gateway = JavaGateway(
            gateway_parameters=GatewayParameters(
                            address="192.168.56.1", # Java Listen Address (IP Where Burp is reachable)
                            port=25333), # Java Listen Port
            callback_server_parameters=CallbackServerParameters(
                            address="192.168.56.103", # Python Callback Address (IP Where Python is reachable)
                            port=25334)) # Python Callback Port
```

# Calling asyncio code in Python from a Java callback 

Py4j creates threads when Java calls Python code, such as in the `ProxyResponseHandler` class above. If you want to call asynchronous code from a callback method (such as from `handleResponseToBeSent`), then post it instead to an event loop running in the main thread. Use `asyncio.run_coroutine_threadsafe`.

```python
import asyncio
from py4j.java_gateway import JavaGateway

async def do_some_async_stuff():
    print("Called async code from Python callback method!")

class ProxyResponseHandler:
    def __init__(self, main_thread_loop, gateway):
        self.main_thread_loop = main_thread_loop
        self.gateway = gateway

    def handleResponseToBeSent(self, response):
        # Posting task to event loop here
        asyncio.run_coroutine_threadsafe(do_some_async_stuff(), self.main_thread_loop)
        return self.gateway.jvm.burp.api.montoya.proxy.http.ProxyResponseToBeSentAction.continueWith(response)

    def handleResponseReceived(self, response):
        return self.gateway.jvm.burp.api.montoya.proxy.http.ProxyResponseReceivedAction.continueWith(response)

    class Java:
        implements = ["burp.api.montoya.proxy.http.ProxyResponseHandler"]

async def main():
    main_thread_loop = asyncio.get_running_loop()
    gateway = JavaGateway()
    gateway.entry_point.proxy().registerResponseHandler(ProxyResponseHandler(main_thread_loop, gateway))
    await asyncio.Event().wait() # Trick to keep loop alive forever

if __name__ == '__main__':
    asyncio.run(main())
```

# Boost Py4j Performance
Py4j was written to be compatible with Python 2. This results in some performance issues that have not been addressed as of writing this (2025.09.13). If you are using a virtual environment, modify Py4j's `decode_bytearray` method in file `protocol.py`. Replace line 246 in file `.venv/lib/python3.12/site-packages/py4j/protocol.py` with `return bytearray2(standard_b64decode(new_bytes))`. 

# Debugging

## Exploring Java objects in Python 

If the information you get back using this extension does not look like what you expect it to in your Python output, you can try a few things to try and interpret it in a way that might make more sense.
- Convert the object to string using `str()`.
- List methods you can call with `dir()`.
- Check the object's type with `type()`.

## Connection errors 
Check the extension's  error output in Burp Suite. That often is enough to diagnose problems.

## I have further issues
Post an issue to this repository and have the solution be added to the readme!

# Security
By default the extension listens on 127.0.0.1:25333. This can be changed in the settings, if for example you work with virtual machines. Connections are unencrypted and unauthenticated. **Do not expose this to untrusted networks.** You can set an authentication token in the settings to gain some protection, but network traffic will still be in plaintext. Furthermore, vulnerabilities in Java or Py4j will expose you.

# Building the Burp Suite extension

Use JDK 21. Build the .jar file using the following command. It will be in `build/libs/`.

    gradle clean build jar

Alternatively, use the official gradle docker container to build the .jar file with the following command.

```
docker run --rm \
  -u "$(id -u):$(id -g)" \
  -v "$PWD":/home/gradle/project \
  -v "$HOME/.gradle":/home/gradle/.gradle \
  -w /home/gradle/project \
  gradle:8.10.2-jdk21 \
  ./gradlew clean build jar --no-daemon
```

The .jar file will be under `build/libs/`.

# Pre-built Releases

Grab the pre-built JAR file at [Releases](https://github.com/PaperTsar/BurpPythonGatewayMontoya/releases).