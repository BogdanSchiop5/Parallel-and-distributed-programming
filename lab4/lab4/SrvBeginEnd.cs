using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

public class SrvBeginEnd
{
    private const int BufferSize = 8192;
    private Socket _conn;
    private byte[] _buffer = new byte[BufferSize];
    private string _host;
    private string _path;
    private List<byte> _headerStream;
    private int _contentLength = -1;
    private byte[] _bodyBuffer;
    private int _bodyOffset;

    private TaskCompletionSource<byte[]> _downloadTcs;

    private SrvBeginEnd(string host, string path, TaskCompletionSource<byte[]> tcs)
    {
        _host = host;
        _path = path;
        _downloadTcs = tcs;
        _conn = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);
    }

    public static Task<byte[]> DownloadAsync(string host, string path)
    {
        var tcs = new TaskCompletionSource<byte[]>();
        var session = new SrvBeginEnd(host, path, tcs);
        session.Start();
        return tcs.Task;
    }

    private void Start()
    {
        try
        {
            var ipHostInfo = Dns.GetHostEntry(_host);
            var ipAddress = ipHostInfo.AddressList.First(a => a.AddressFamily == AddressFamily.InterNetwork);
            var remoteEP = new IPEndPoint(ipAddress, 80);

            _headerStream = new List<byte>();
            _conn.BeginConnect(remoteEP, new AsyncCallback(OnConnectDone), null);
        }
        catch (Exception ex)
        {
            SetCompletion(null, ex);
        }
    }


    private void OnConnectDone(IAsyncResult ar)
    {
        try
        {
            _conn.EndConnect(ar);
            Console.WriteLine($"-> Connected to {_host}");
            SendRequest();
        }
        catch (Exception ex) { SetCompletion(null, ex); }
    }

    private void SendRequest()
    {
        string request = $"GET {_path} HTTP/1.1\r\nHost: {_host}\r\nConnection: close\r\n\r\n";
        byte[] requestBytes = Encoding.ASCII.GetBytes(request);
        _conn.BeginSend(requestBytes, 0, requestBytes.Length, SocketFlags.None, new AsyncCallback(OnSendDone), null);
    }

    private void OnSendDone(IAsyncResult ar)
    {
        try
        {
            _conn.EndSend(ar);
            Console.WriteLine($"-> Request sent for {_path}");
            _conn.BeginReceive(_buffer, 0, BufferSize, SocketFlags.None, new AsyncCallback(OnReceiveHeader), null);
        }
        catch (Exception ex) { SetCompletion(null, ex); }
    }

    private void OnReceiveHeader(IAsyncResult ar)
    {
        try
        {
            int bytesRead = _conn.EndReceive(ar);
            if (bytesRead == 0) throw new EndOfStreamException("Connection closed before full header received.");

            _headerStream.AddRange(_buffer.Take(bytesRead));

            string currentData = Encoding.ASCII.GetString(_headerStream.ToArray());
            int headerEndIndex = currentData.IndexOf("\r\n\r\n");

            if (headerEndIndex >= 0)
            {
                _contentLength = ParseContentLength(currentData);
                if (_contentLength <= 0) throw new InvalidOperationException("Failed to parse valid Content-Length.");

                _bodyBuffer = new byte[_contentLength];
                headerEndIndex += 4;
                int alreadyRead = _headerStream.Count - headerEndIndex;
                _bodyOffset = 0;

                if (alreadyRead > 0)
                {
                    Array.Copy(_headerStream.ToArray(), headerEndIndex, _bodyBuffer, 0, alreadyRead);
                    _bodyOffset = alreadyRead;
                }

                BeginReceiveBody();
            }
            else
            {
                _conn.BeginReceive(_buffer, 0, BufferSize, SocketFlags.None, new AsyncCallback(OnReceiveHeader), null);
            }
        }
        catch (Exception ex) { SetCompletion(null, ex); }
    }

    private void BeginReceiveBody()
    {
        if (_bodyOffset >= _contentLength)
        {
            SetCompletion(_bodyBuffer, null);
            return;
        }

        int remaining = _contentLength - _bodyOffset;
        int size = Math.Min(BufferSize, remaining);
        _conn.BeginReceive(_bodyBuffer, _bodyOffset, size, SocketFlags.None, new AsyncCallback(OnReceiveBody), null);
    }

    private void OnReceiveBody(IAsyncResult ar)
    {
        try
        {
            int bytesRead = _conn.EndReceive(ar);

            if (bytesRead == 0 && _bodyOffset < _contentLength) throw new EndOfStreamException("Connection closed before file was fully downloaded.");

            _bodyOffset += bytesRead;
            BeginReceiveBody();
        }
        catch (Exception ex) { SetCompletion(null, ex); }
    }

    private void SetCompletion(byte[] result, Exception ex)
    {
        if (_conn.Connected) { try { _conn.Shutdown(SocketShutdown.Both); } catch { } }
        _conn.Close();

        if (ex != null)
        {
            _downloadTcs.SetException(ex);
        }
        else
        {
            _downloadTcs.SetResult(result);
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