using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading.Tasks;
using System.Collections.Generic;
using System.Linq;
using System.IO;

public class SrvAwait
{
    private const int BufferSize = 8192;
    private Socket _conn;
    private byte[] _buffer;
    private string _host;
    private string _path;
    private int _contentLength = -1;
    private List<byte> _headerStream;
    private byte[] _bodyBuffer;
    private int _bodyOffset;

    public static Task<byte[]> DownloadAsync(string host, string path)
    {
        var session = new SrvAwait(host, path);
        return session.StartDownload();
    }

    public SrvAwait(string host, string path)
    {
        _host = host;
        _path = path;
        _conn = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
        _buffer = new byte[BufferSize];
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

    static Task Connect(Socket conn, EndPoint remoteEP)
    {
        var promise = new TaskCompletionSource<bool>();
        conn.BeginConnect(remoteEP,
            (IAsyncResult ar) =>
            {
                try { conn.EndConnect(ar); promise.SetResult(true); }
                catch (Exception ex) { promise.SetException(ex); }
            }, null);
        return promise.Task;
    }

    public async Task<byte[]> StartDownload()
    {
        try
        {
            var ipHostInfo = await Dns.GetHostEntryAsync(_host);
            var ipAddress = ipHostInfo.AddressList.First(a => a.AddressFamily == AddressFamily.InterNetwork);
            var remoteEP = new IPEndPoint(ipAddress, 80);

            await Connect(_conn, remoteEP);
            Console.WriteLine($"-> Connected to {_host}");

            string request = $"GET {_path} HTTP/1.1\r\nHost: {_host}\r\nConnection: close\r\n\r\n";
            byte[] requestBytes = Encoding.ASCII.GetBytes(request);
            await Send(_conn, requestBytes, 0, requestBytes.Length);
            Console.WriteLine($"-> Request sent for {_path}");

            _headerStream = new List<byte>();
            await ReceiveHeaderAndSetupBody();
            await ReceiveBody();

            return _bodyBuffer;
        }
        finally
        {
            if (_conn.Connected) { try { _conn.Shutdown(SocketShutdown.Both); } catch { } }
            _conn.Close();
        }
    }

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
                _contentLength = ParseContentLength(currentData);
                if (_contentLength <= 0) throw new InvalidOperationException("Could not determine Content-Length.");

                _bodyBuffer = new byte[_contentLength];
                headerEndIndex += 4;
                int alreadyRead = _headerStream.Count - headerEndIndex;
                _bodyOffset = 0;

                if (alreadyRead > 0)
                {
                    Array.Copy(_headerStream.ToArray(), headerEndIndex, _bodyBuffer, 0, alreadyRead);
                    _bodyOffset = alreadyRead;
                }
                return;
            }
            if (_headerStream.Count > BufferSize * 4) throw new InvalidOperationException("Header too large.");
        }
        throw new EndOfStreamException("Connection closed before full header received.");
    }

    private async Task ReceiveBody()
    {
        while (_bodyOffset < _contentLength)
        {
            int remaining = _contentLength - _bodyOffset;
            int size = Math.Min(BufferSize, remaining);

            int bytesRead = await Receive(_conn, _bodyBuffer, _bodyOffset, size);

            if (bytesRead == 0) throw new EndOfStreamException("Connection closed unexpectedly.");

            _bodyOffset += bytesRead;
        }
    }

    private static int ParseContentLength(string header)
    {
        var lines = header.Split(new[] { "\r\n" }, StringSplitOptions.RemoveEmptyEntries);
        var clLine = lines.FirstOrDefault(l => l.StartsWith("Content-Length:", StringComparison.OrdinalIgnoreCase));

        if (clLine != null && int.TryParse(clLine.Split(':')[1].Trim(), out int length)) return length;
        return -1;
    }
}