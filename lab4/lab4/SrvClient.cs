using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using System.IO;

public class SrvClient
{
    private static readonly List<(string host, string path, string name)> FilesToDownload =
        new List<(string host, string path, string name)>
        {
            ("www.cs.ubbcluj.ro", "/~rlupsa/edu/pdp/", "PDP_Assignment_Page.html"),
            
            ("invalid.host.12345", "/", "INVALID_HOST.txt"), 
            
            ("httpbin.org", "/html", "HTTPBIN_TestPage.html")
        };

    private delegate Task<byte[]> DownloadMethod(string host, string path);

    public static void Main(string[] args)
    {

        DownloadMethod selectedMethod = GetUserMethodSelection();

        if (selectedMethod == null)
        {
            Console.WriteLine("Invalid selection. Exiting.");
            return;
        }

        Console.WriteLine($"\n--- Starting Concurrent Downloads using: {selectedMethod.Method.DeclaringType.Name} ---");

        var concurrentTasks = FilesToDownload.Select(target =>
            ExecuteDownloadAndReport(selectedMethod, target.host, target.path, target.name)
        ).ToList();

        try
        {
            Task.WhenAll(concurrentTasks).Wait();
            Console.WriteLine("\n--- All Downloads Finished ---");
        }
        catch (AggregateException ae)
        {
            Console.WriteLine("\n--- Unexpected Critical Errors ---");
            foreach (var ex in ae.Flatten().InnerExceptions)
            {
                Console.WriteLine($"Critical Error: {ex.Message}");
            }
        }
    }

    private static DownloadMethod GetUserMethodSelection()
    {
        Console.WriteLine("1.(SrvBeginEnd)");
        Console.WriteLine("2.(SrvTasksLoop)");
        Console.WriteLine("3.(SrvAwait)");
        Console.Write("Enter choice: ");

        string input = Console.ReadLine();

        switch (input)
        {
            case "1":
                return SrvBeginEnd.DownloadAsync;
            case "2":
                return SrvTasksLoop.DownloadAsync;
            case "3":
                return SrvAwait.DownloadAsync;
            default:
                return null;
        }
    }

    private static async Task ExecuteDownloadAndReport(
        DownloadMethod method,
        string host,
        string path,
        string name)
    {
        string methodName = method.Method.DeclaringType.Name;

        try
        {
            byte[] data = await method(host, path);

            string filePath = Path.Combine(Environment.CurrentDirectory, name);
            System.IO.File.WriteAllBytes(filePath, data);

            Console.WriteLine($"\n[SUCCESS - {methodName}] {name}: Downloaded {data.Length} bytes.");
            Console.WriteLine($"Saved file to: {filePath}");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"\n[FAILURE - {methodName}] {name}: {ex.GetType().Name}: {ex.Message}");
        }
    }
}