using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading.Tasks;
using System.Collections.Generic;
using System.Linq;
using System.IO;


static class TaskHelper
{
    private static void auxLoop<R>(Func<R, bool> loopingPredicate, Func<R, Task<R>> loopFunc, R start, TaskCompletionSource<R> ret)
    {
        if (!loopingPredicate(start))
        {
            ret.SetResult(start);
            return;
        }

        Task<R> tmpResFuture = loopFunc(start);
        tmpResFuture.ContinueWith((Task<R> v) => {
            if (v.IsFaulted) { ret.SetException(v.Exception.InnerException); }
            else { auxLoop(loopingPredicate, loopFunc, v.Result, ret); }
        });
    }

    public static Task<R> executeAsyncLoop<R>(Func<R, bool> loopingPredicate, Func<R, Task<R>> loopFunc, R start)
    {
        TaskCompletionSource<R> ret = new TaskCompletionSource<R>();
        auxLoop(loopingPredicate, loopFunc, start, ret);
        return ret.Task;
    }
}

struct BodyReceiveState
{
    public byte[] bodyBuffer;
    public int totalLength;
    public int offset;
    public int bufferSize;

    public static BodyReceiveState Init(byte[] buffer, int totalLen, int startOffset)
    {
        return new BodyReceiveState
        {
            bodyBuffer = buffer,
            totalLength = totalLen,
            offset = startOffset,
            bufferSize = 8192
        };
    }
}

public class SrvTasksLoop
{
    private Socket _conn;
    private byte[] _buffer = new byte[8192];
    private string _host;
    private string _path;

    public static Task<byte[]> DownloadAsync(string host, string path)
    {
        var session = new SrvTasksLoop(host, path);
        return session.StartDownload();
    }

    public SrvTasksLoop(string host, string path)
    {
        _host = host;
        _path = path;
        _conn = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
    }

    static Task Connect(Socket conn, EndPoint remoteEP)
    {
        var promise = new TaskCompletionSource<bool>();
        conn.BeginConnect(remoteEP, (IAsyncResult ar) => {
            try { conn.EndConnect(ar); promise.SetResult(true); }
            catch (Exception ex) { promise.SetException(ex); }
        }, null);
        return promise.Task;
    }

    static Task<int> Send(Socket conn, byte[] buf, int index, int count)
    {
        var promise = new TaskCompletionSource<int>();
        conn.BeginSend(buf, index, count, SocketFlags.None,
            (IAsyncResult ar) => {
                try { promise.SetResult(conn.EndSend(ar)); }
                catch (Exception ex) { promise.SetException(ex); }
            }, null);
        return promise.Task;
    }

    static Task<int> Receive(Socket conn, byte[] buf, int index, int count)
    {
        var promise = new TaskCompletionSource<int>();
        conn.BeginReceive(buf, index, count, SocketFlags.None,
            (IAsyncResult ar) => {
                try { promise.SetResult(conn.EndReceive(ar)); }
                catch (Exception ex) { promise.SetException(ex); }
            }, null);
        return promise.Task;
    }

    public Task<byte[]> StartDownload()
    {
        var downloadTcs = new TaskCompletionSource<byte[]>();

        Task.Run(() =>
        {
            try
            {
                var ipHostInfo = Dns.GetHostEntry(_host);
                var ipAddress = ipHostInfo.AddressList.First(a => a.AddressFamily == AddressFamily.InterNetwork);
                var remoteEP = new IPEndPoint(ipAddress, 80);

                Connect(_conn, remoteEP)
                .ContinueWith(tConnect =>
                {
                    if (tConnect.IsFaulted) throw tConnect.Exception.InnerException;
                    Console.WriteLine($"-> Connected to {_host}");

                    string request = $"GET {_path} HTTP/1.1\r\nHost: {_host}\r\nConnection: close\r\n\r\n";
                    byte[] requestBytes = Encoding.ASCII.GetBytes(request);
                    return Send(_conn, requestBytes, 0, requestBytes.Length);
                })
                .Unwrap()
                .ContinueWith(tSend =>
                {
                    if (tSend.IsFaulted) throw tSend.Exception.InnerException;
                    Console.WriteLine($"-> Request sent for {_path}");

                    var stream = new List<byte>();
                    return ReceiveHeaderLoop(stream);
                })
                .Unwrap()
                .ContinueWith(tHeader =>
                {
                    if (tHeader.IsFaulted) throw tHeader.Exception.InnerException;

                    string headerText = Encoding.ASCII.GetString(tHeader.Result.ToArray());
                    int contentLength = ParseContentLength(headerText);
                    if (contentLength <= 0) throw new InvalidOperationException("Could not determine Content-Length.");

                    int headerEnd = headerText.IndexOf("\r\n\r\n") + 4;
                    int alreadyRead = tHeader.Result.Count - headerEnd;
                    var bodyBuffer = new byte[contentLength];

                    if (alreadyRead > 0) Array.Copy(tHeader.Result.ToArray(), headerEnd, bodyBuffer, 0, alreadyRead);

                    BodyReceiveState startState = BodyReceiveState.Init(bodyBuffer, contentLength, alreadyRead);
                    return ReceiveBodyLoop(startState);
                })
                .Unwrap()
                .ContinueWith(tBodyState =>
                {
                    if (tBodyState.IsFaulted) { downloadTcs.SetException(tBodyState.Exception.InnerException); }
                    else { downloadTcs.SetResult(tBodyState.Result.bodyBuffer); }
                    _conn.Close();
                });
            }
            catch (Exception ex)
            {
                downloadTcs.SetException(ex);
                _conn.Close();
            }
        });

        return downloadTcs.Task;
    }

    private Task<List<byte>> ReceiveHeaderLoop(List<byte> stream)
    {
        return Receive(_conn, _buffer, 0, _buffer.Length)
        .ContinueWith(t =>
        {
            if (t.IsFaulted) throw t.Exception.InnerException;
            int bytesRead = t.Result;
            if (bytesRead == 0) throw new EndOfStreamException("Connection closed unexpectedly.");

            stream.AddRange(_buffer.Take(bytesRead));

            string currentData = Encoding.ASCII.GetString(stream.ToArray());
            if (currentData.Contains("\r\n\r\n")) return Task.FromResult(stream);

            return ReceiveHeaderLoop(stream);
        })
        .Unwrap();
    }

    private Task<BodyReceiveState> ReceiveBodyLoop(BodyReceiveState currentState)
    {
        return TaskHelper.executeAsyncLoop(
            (BodyReceiveState state) => state.offset < state.totalLength,
            (BodyReceiveState state) =>
            {
                int remaining = state.totalLength - state.offset;
                int size = Math.Min(state.bufferSize, remaining);

                return Receive(_conn, state.bodyBuffer, state.offset, size)
                .ContinueWith(t =>
                {
                    if (t.IsFaulted) throw t.Exception.InnerException;
                    int bytesRead = t.Result;
                    if (bytesRead == 0) throw new EndOfStreamException("Connection closed unexpectedly.");

                    state.offset += bytesRead;
                    return state;
                });
            },
            currentState);
    }

    private static int ParseContentLength(string header)
    {
        var lines = header.Split(new[] { "\r\n" }, StringSplitOptions.RemoveEmptyEntries);
        var clLine = lines.FirstOrDefault(l => l.StartsWith("Content-Length:", StringComparison.OrdinalIgnoreCase));

        if (clLine != null && int.TryParse(clLine.Split(':')[1].Trim(), out int length)) return length;
        return -1;
    }
}