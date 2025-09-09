using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Erioo_Gamepad_Server
{
    internal class ClientController
    {
        private IXbox360Controller controller;
        private float prevX = 0f;
        private float prevY = 0f;
        private const float threshold = 0.01f;

        public ClientController(IXbox360Controller c)
        {
            controller = c;
        }

        private bool firstJoyUpdate = true;

        public void HandleJoyMessage(string msg, IXbox360Controller controller)
        {
            var parts = msg.Split(' ');
            if (parts.Length != 3) return;
            if (!float.TryParse(parts[1], out float x)) return;
            if (!float.TryParse(parts[2], out float y)) return;
            bool isNearZero = Math.Abs(x) < 0.1f && Math.Abs(y) < 0.1f;
            if (firstJoyUpdate || isNearZero || Math.Abs(x - prevX) >= threshold || Math.Abs(y - prevY) >= threshold)
            {
                controller.SetAxisValue(Xbox360Axis.LeftThumbX.Id, (short)(x * short.MaxValue));
                controller.SetAxisValue(Xbox360Axis.LeftThumbY.Id, (short)(y * short.MaxValue));

                prevX = x;
                prevY = y;
                firstJoyUpdate = false;
            }

            if (isNearZero) firstJoyUpdate = true;
        }
    }

}
