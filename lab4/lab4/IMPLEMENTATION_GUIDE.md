# Implementation Guide - Lab 4 HTTP Downloader

This document explicitly shows where each of the three required implementations is located in the codebase.

---

## Overview

The program implements three different approaches to download files via HTTP using Socket's APM (Asynchronous Programming Model) methods:
1. **Event-driven (callbacks)** - `SrvBeginEnd.cs`
2. **Tasks with ContinueWith()** - `SrvTasksLoop.cs` (Bonus)
3. **Async/Await** - `SrvAwait.cs`

All three are orchestrated from `SrvClient.cs` which runs them concurrently.

---

## 1. Event-Driven Implementation (Callbacks)

**File:** `lab4/SrvBeginEnd.cs`

### Key Characteristics:
- Uses `AsyncCallback` delegates directly
- HTTP parser logic is embedded in callback methods
- Uses `ManualResetEvent` for synchronization

### Key Sections:

#### Socket Operations with Callbacks:
```csharp
// Line 61: BeginConnect with callback
_conn.BeginConnect(remoteEP, new AsyncCallback(OnConnectDone), null);

// Line 69-78: Connect callback - parser logic in callback
private void OnConnectDone(IAsyncResult ar)
{
    _conn.EndConnect(ar);
    SendRequest();  // Chains to next operation
}

// Line 84: BeginSend with callback
_conn.BeginSend(requestBytes, 0, requestBytes.Length, SocketFlags.None, 
    new AsyncCallback(OnSendDone), null);

// Line 93: BeginReceive with callback for header parsing
_conn.BeginReceive(_buffer, 0, BufferSize, SocketFlags.None, 
    new AsyncCallback(OnReceiveHeader), null);
```

#### HTTP Parser in Callback (Lines 98-134):
```csharp
private void OnReceiveHeader(IAsyncResult ar)
{
    int bytesRead = _conn.EndReceive(ar);
    _headerStream.AddRange(_buffer.Take(bytesRead));
    
    string currentData = Encoding.ASCII.GetString(_headerStream.ToArray());
    int headerEndIndex = currentData.IndexOf("\r\n\r\n");
    
    if (headerEndIndex >= 0)
    {
        // Parser logic directly in callback
        _contentLength = ParseContentLength(currentData);
        // ... setup body buffer and continue
        BeginReceiveBody();
    }
    else
    {
        // Continue receiving header in callback
        _conn.BeginReceive(_buffer, 0, BufferSize, SocketFlags.None, 
            new AsyncCallback(OnReceiveHeader), null);
    }
}
```

#### Body Reception Callback (Lines 149-161):
```csharp
private void OnReceiveBody(IAsyncResult ar)
{
    int bytesRead = _conn.EndReceive(ar);
    _bodyOffset += bytesRead;
    BeginReceiveBody();  // Recursive callback pattern
}
```

#### Synchronization (Lines 25, 43, 170):
```csharp
private ManualResetEvent _doneEvent = new ManualResetEvent(false);
// ...
session._doneEvent.WaitOne();  // Blocking wait (allowed in wrapper)
// ...
_doneEvent.Set();  // Signal completion
```

#### Usage in Main (SrvClient.cs, Lines 23-40):
```csharp
Task t1 = Task.Run(() =>
{
    byte[] data = SrvBeginEnd.DownloadFile(target.host, target.path);
    // ... save file
});
```

---

## 2. Tasks with ContinueWith() Implementation (Bonus)

**File:** `lab4/SrvTasksLoop.cs`

### Key Characteristics:
- Wraps Begin/End operations in Tasks using `TaskCompletionSource`
- Chains operations using `ContinueWith()`
- HTTP parser logic is in continuation callbacks

### Key Sections:

#### APM to TAP Wrappers (Lines 71-101):
```csharp
// Wrap BeginConnect/EndConnect in Task
static Task Connect(Socket conn, EndPoint remoteEP)
{
    var promise = new TaskCompletionSource<bool>();
    conn.BeginConnect(remoteEP, (IAsyncResult ar) => {
        try { 
            conn.EndConnect(ar); 
            promise.SetResult(true);  // Callback sets task result
        }
        catch (Exception ex) { promise.SetException(ex); }
    }, null);
    return promise.Task;
}

// Similar wrappers for Send and Receive
static Task<int> Send(Socket conn, byte[] buf, int index, int count)
static Task<int> Receive(Socket conn, byte[] buf, int index, int count)
```

#### Task Chaining with ContinueWith() (Lines 118-166):
```csharp
// Chain: Connect -> Send -> Receive Header -> Parse -> Receive Body
Connect(_conn, remoteEP)
.ContinueWith(tConnect =>
{
    if (tConnect.IsFaulted) throw tConnect.Exception.InnerException;
    Console.WriteLine($"-> Connected to {_host}");
    
    // Return next task in chain
    return Send(_conn, requestBytes, 0, requestBytes.Length);
})
.Unwrap()  // Unwrap Task<Task<T>> to Task<T>
.ContinueWith(tSend =>
{
    if (tSend.IsFaulted) throw tSend.Exception.InnerException;
    Console.WriteLine($"-> Request sent for {_path}");
    
    // Start header receive loop
    return ReceiveHeaderLoop(stream);
})
.Unwrap()
.ContinueWith(tHeader =>
{
    // HTTP parser logic in continuation
    string headerText = Encoding.ASCII.GetString(tHeader.Result.ToArray());
    int contentLength = ParseContentLength(headerText);
    // ... parse and setup body
    return ReceiveBodyLoop(startState);
})
.Unwrap()
.ContinueWith(tBodyState =>
{
    // Final completion
    if (tBodyState.IsFaulted) { 
        downloadTcs.SetException(tBodyState.Exception.InnerException); 
    }
    else { 
        downloadTcs.SetResult(tBodyState.Result.bodyBuffer); 
    }
});
```

#### Recursive Task Loop for Header (Lines 178-195):
```csharp
private Task<List<byte>> ReceiveHeaderLoop(List<byte> stream)
{
    return Receive(_conn, _buffer, 0, _buffer.Length)
    .ContinueWith(t =>
    {
        int bytesRead = t.Result;
        stream.AddRange(_buffer.Take(bytesRead));
        
        string currentData = Encoding.ASCII.GetString(stream.ToArray());
        if (currentData.Contains("\r\n\r\n")) 
            return Task.FromResult(stream);
        
        // Recursive continuation
        return ReceiveHeaderLoop(stream);
    })
    .Unwrap();
}
```

#### Body Receive Loop with TaskHelper (Lines 197-218):
```csharp
private Task<BodyReceiveState> ReceiveBodyLoop(BodyReceiveState currentState)
{
    return TaskHelper.executeAsyncLoop(
        (BodyReceiveState state) => state.offset < state.totalLength,
        (BodyReceiveState state) =>
        {
            return Receive(_conn, state.bodyBuffer, state.offset, size)
            .ContinueWith(t =>
            {
                state.offset += t.Result;
                return state;
            });
        },
        currentState);
}
```

#### Usage in Main (SrvClient.cs, Lines 42-43, 50):
```csharp
SrvTasksLoop session2 = new SrvTasksLoop(FilesToDownload[1].host, FilesToDownload[1].path);
Task<byte[]> t2 = session2.StartDownload();
// ...
ProcessTaskResult(t2, FilesToDownload[1].name, "Tasks/Loop")
```

---

## 3. Async/Await Implementation

**File:** `lab4/SrvAwait.cs`

### Key Characteristics:
- Wraps Begin/End operations in Tasks (same as #2)
- Uses `async/await` syntax instead of `ContinueWith()`
- HTTP parser logic in async methods

### Key Sections:

#### TAP Wrappers (Lines 31-62):
```csharp
// Same wrappers as SrvTasksLoop
static Task<int> Receive(Socket conn, byte[] buf, int index, int count)
static Task<int> Send(Socket conn, byte[] buf, int index, int count)
static Task Connect(Socket conn, EndPoint remoteEP)
```

#### Async/Await Main Flow (Lines 66-93):
```csharp
public async Task<byte[]> StartDownload()
{
    try
    {
        var ipHostInfo = await Dns.GetHostEntryAsync(_host);
        var ipAddress = ipHostInfo.AddressList.First(...);
        var remoteEP = new IPEndPoint(ipAddress, 80);
        
        // Sequential await instead of ContinueWith chains
        await Connect(_conn, remoteEP);
        Console.WriteLine($"-> Connected to {_host}");
        
        string request = $"GET {_path} HTTP/1.1\r\nHost: {_host}\r\nConnection: close\r\n\r\n";
        byte[] requestBytes = Encoding.ASCII.GetBytes(request);
        await Send(_conn, requestBytes, 0, requestBytes.Length);
        Console.WriteLine($"-> Request sent for {_path}");
        
        _headerStream = new List<byte>();
        await ReceiveHeaderAndSetupBody();  // Async method
        await ReceiveBody();                 // Async method
        
        return _bodyBuffer;
    }
    finally
    {
        // Cleanup
    }
}
```

#### HTTP Parser in Async Method (Lines 95-128):
```csharp
private async Task ReceiveHeaderAndSetupBody()
{
    while (true)
    {
        int bytesRead = await Receive(_conn, _buffer, 0, BufferSize);
        if (bytesRead == 0) break;
        
        _headerStream.AddRange(_buffer.Take(bytesRead));
        
        string currentData = Encoding.ASCII.GetString(_headerStream.ToArray());
        int headerEndIndex = currentData.IndexOf("\r\n\r\n");
        
        if (headerEndIndex >= 0)
        {
            // Parser logic in async method
            _contentLength = ParseContentLength(currentData);
            if (_contentLength <= 0) 
                throw new InvalidOperationException("Could not determine Content-Length.");
            
            _bodyBuffer = new byte[_contentLength];
            // ... setup body buffer
            return;  // Exit loop
        }
    }
}
```

#### Body Reception with Async/Await (Lines 130-143):
```csharp
private async Task ReceiveBody()
{
    while (_bodyOffset < _contentLength)
    {
        int remaining = _contentLength - _bodyOffset;
        int size = Math.Min(BufferSize, remaining);
        
        int bytesRead = await Receive(_conn, _bodyBuffer, _bodyOffset, size);
        
        if (bytesRead == 0) 
            throw new EndOfStreamException("Connection closed unexpectedly.");
        
        _bodyOffset += bytesRead;
    }
}
```

#### Usage in Main (SrvClient.cs, Lines 45-46, 51):
```csharp
SrvAwait session3 = new SrvAwait(FilesToDownload[2].host, FilesToDownload[2].path);
Task<byte[]> t3 = session3.StartDownload();
// ...
ProcessTaskResult(t3, FilesToDownload[2].name, "Async/Await")
```

---

## Common Components

### HTTP Parser (All Three Files)

All three implementations use the same `ParseContentLength` method:

```csharp
private static int ParseContentLength(string header)
{
    var lines = header.Split(new[] { "\r\n" }, StringSplitOptions.RemoveEmptyEntries);
    var clLine = lines.FirstOrDefault(l => 
        l.StartsWith("Content-Length:", StringComparison.OrdinalIgnoreCase));
    
    if (clLine != null && int.TryParse(clLine.Split(':')[1].Trim(), out int length)) 
        return length;
    return -1;
}
```

- **SrvBeginEnd.cs:** Line 173-180
- **SrvTasksLoop.cs:** Line 220-227
- **SrvAwait.cs:** Line 145-152

### Main Orchestration (SrvClient.cs)

**File:** `lab4/SrvClient.cs`

Lines 19-67 orchestrate all three implementations concurrently:

```csharp
public static void Main(string[] args)
{
    // 1. Event-driven (callbacks)
    Task t1 = Task.Run(() => {
        byte[] data = SrvBeginEnd.DownloadFile(...);
    });
    
    // 2. Tasks with ContinueWith()
    SrvTasksLoop session2 = new SrvTasksLoop(...);
    Task<byte[]> t2 = session2.StartDownload();
    
    // 3. Async/Await
    SrvAwait session3 = new SrvAwait(...);
    Task<byte[]> t3 = session3.StartDownload();
    
    // Wait for all (only Wait() call, allowed in Main)
    Task.WhenAll(taskList).Wait();
}
```

---

## Summary Table

| Implementation | File | Key Method | Socket Wrappers | Control Flow |
|----------------|------|------------|-----------------|--------------|
| **Event-driven** | `SrvBeginEnd.cs` | `DownloadFile()` | Direct `Begin/End` with `AsyncCallback` | Callback methods chain operations |
| **Tasks/ContinueWith** | `SrvTasksLoop.cs` | `StartDownload()` | `TaskCompletionSource` wrappers | `ContinueWith().Unwrap()` chains |
| **Async/Await** | `SrvAwait.cs` | `StartDownload()` | `TaskCompletionSource` wrappers | `async/await` syntax |

---

## Key Differences

1. **SrvBeginEnd (Callbacks):**
   - Parser logic in callback methods (`OnReceiveHeader`, `OnReceiveBody`)
   - Uses `ManualResetEvent` for completion
   - Most "traditional" APM pattern

2. **SrvTasksLoop (ContinueWith):**
   - Parser logic in `ContinueWith` lambdas
   - Uses `TaskCompletionSource` to wrap APM
   - Explicit task chaining with `.Unwrap()`
   - Uses helper for recursive loops

3. **SrvAwait (Async/Await):**
   - Parser logic in `async` methods
   - Same Task wrappers as #2
   - Cleaner, more readable sequential flow
   - Uses `while` loops instead of recursive continuations

---

## Socket Operations Used

All three implementations use the required Socket APM methods:

- ✅ `BeginConnect()` / `EndConnect()` - Connection establishment
- ✅ `BeginSend()` / `EndSend()` - Sending HTTP request
- ✅ `BeginReceive()` / `EndReceive()` - Receiving HTTP response

## HTTP Parser Requirements

All three implementations meet the parser requirements:

- ✅ Parse header lines (look for `\r\n\r\n` delimiter)
- ✅ Extract `Content-Length:` header value
- ✅ Use `Content-Length` to determine body size

---

## Notes

- The only `Wait()` call is in `Main()` (line 56 of `SrvClient.cs`), which is allowed per requirements
- All three implementations run concurrently when executed
- Error handling is implemented in all three approaches
- Socket cleanup is handled in all implementations

