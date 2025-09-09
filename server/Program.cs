using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;
using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading.Tasks;

namespace Erioo_Gamepad_Server
{
    internal class Program
    {
        static void Main(string[] args)
        {
            TcpListener server = new TcpListener(IPAddress.Any, 9000);
            server.Start();
            Console.WriteLine("Server started on port 9000...");

            var vigemClient = new ViGEmClient();
            var clientControllers = new Dictionary<TcpClient, IXbox360Controller>();

            while (true)
            {
                TcpClient client = server.AcceptTcpClient();
                Console.WriteLine($"Client connected! {client.Client.RemoteEndPoint.ToString()}");
                var controller = vigemClient.CreateXbox360Controller();
                controller.Connect();
                clientControllers[client] = controller;
                ClientController clientController = new ClientController(controller);
                Task.Run(() =>
                {
                    using (client)
                    {
                        NetworkStream stream = client.GetStream();
                        byte[] buffer = new byte[1024];
                        while (true)
                        {
                            try
                            {
                                int bytesRead = stream.Read(buffer, 0, buffer.Length);
                                if (bytesRead == 0) break;
                                string chunk = Encoding.UTF8.GetString(buffer, 0, bytesRead);
                                var lines = chunk.Split(new[] { '\n', '\r' }, StringSplitOptions.RemoveEmptyEntries);
                                foreach (var line in lines)
                                {
                                    ///Console.WriteLine($"Client {client.Client.RemoteEndPoint}: {line}");
                                    HandleInput(controller, line, clientController);
                                }
                            }
                            catch
                            {
                                break;
                            }
                        }
                        Console.WriteLine("Client disconnected.");
                        controller.Disconnect();
                        clientControllers.Remove(client);
                    }
                });
            }
        }

        static void HandleInput(IXbox360Controller controller, string msg, ClientController clientController)
        {
            switch (msg)
            {
                case "A": controller.SetButtonState(Xbox360Button.A, true); break;
                case "A_UP": controller.SetButtonState(Xbox360Button.A, false); break;
                case "B": controller.SetButtonState(Xbox360Button.B, true); break;
                case "B_UP": controller.SetButtonState(Xbox360Button.B, false); break;
                case "X": controller.SetButtonState(Xbox360Button.X, true); break;
                case "X_UP": controller.SetButtonState(Xbox360Button.X, false); break;
                case "Y": controller.SetButtonState(Xbox360Button.Y, true); break;
                case "Y_UP": controller.SetButtonState(Xbox360Button.Y, false); break;
                case "START": controller.SetButtonState(Xbox360Button.Start, true); break;
                case "START_UP": controller.SetButtonState(Xbox360Button.Start, false); break;
                case "BACK": controller.SetButtonState(Xbox360Button.Back, true); break;
                case "BACK_UP": controller.SetButtonState(Xbox360Button.Back, false); break;
                default:
                    if (msg.StartsWith("JOY"))
                    {
                        clientController.HandleJoyMessage(msg, controller);
                    }
                    break;
            }
            controller.SubmitReport();
        }
    }
}
