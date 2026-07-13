#property strict
#property version   "0.20"
#property description "Read-only MNM AU Seekers quote and indicator bridge"

input string InpEndpoint = "http://127.0.0.1:8000/api/v1/market-data/mt5/snapshots";
input string InpBridgeToken = "";
input int InpPublishSeconds = 30;
input bool InpUseClosedBar = true;

int OnInit()
{
   if(StringLen(InpEndpoint) == 0 || StringLen(InpBridgeToken) == 0)
   {
      Print("Bridge disabled: configure the endpoint and a bridge token in EA inputs.");
      return(INIT_PARAMETERS_INCORRECT);
   }

   if(InpPublishSeconds < 10)
   {
      Print("Bridge disabled: publish interval must be at least 10 seconds.");
      return(INIT_PARAMETERS_INCORRECT);
   }

   EventSetTimer(InpPublishSeconds);
   Print("MNM AU Seekers read-only bridge started. Allow the endpoint URL in MT5 WebRequest settings.");
   return(INIT_SUCCEEDED);
}

void OnDeinit(const int reason)
{
   EventKillTimer();
}

void OnTick()
{
   // Publishing is timer-driven to avoid sending on every market tick.
}

void OnTimer()
{
   PublishSnapshot();
}

void PublishSnapshot()
{
   MqlTick tick;
   if(!SymbolInfoTick(_Symbol, tick) || tick.bid <= 0.0 || tick.ask <= 0.0)
   {
      Print("Bridge skipped publish: no valid tick is available for ", _Symbol, ".");
      return;
   }

   string m15;
   string h1;
   string h4;
   if(!BuildTimeframeJson(PERIOD_M15, "M15", m15)
      || !BuildTimeframeJson(PERIOD_H1, "H1", h1)
      || !BuildTimeframeJson(PERIOD_H4, "H4", h4))
   {
      Print("Bridge skipped publish: one or more indicator buffers are not ready.");
      return;
   }

   string capturedAt = TimeToIso8601(TimeGMT());
   string payload = "{"
      + "\"schema_version\":\"1.0\","
      + "\"source\":\"mt5\","
      + "\"symbol\":\"" + JsonEscape(_Symbol) + "\","
      + "\"captured_at\":\"" + capturedAt + "\","
      + "\"bid\":" + Number(tick.bid, _Digits) + ","
      + "\"ask\":" + Number(tick.ask, _Digits) + ","
      + "\"timeframes\":[" + m15 + "," + h1 + "," + h4 + "]"
      + "}";

   char request[];
   int encoded = StringToCharArray(payload, request, 0, WHOLE_ARRAY, CP_UTF8);
   if(encoded <= 1)
   {
      Print("Bridge skipped publish: payload encoding failed.");
      return;
   }
   ArrayResize(request, encoded - 1);

   char response[];
   string responseHeaders;
   string headers = "Content-Type: application/json\r\n"
      + "X-Bridge-Token: " + InpBridgeToken + "\r\n";
   ResetLastError();
   int statusCode = WebRequest(
      "POST",
      InpEndpoint,
      headers,
      5000,
      request,
      response,
      responseHeaders
   );

   if(statusCode == -1)
   {
      Print("Bridge request failed with MT5 error ", GetLastError(),
         ". Confirm the endpoint is allowed under Tools > Options > Expert Advisors.");
      return;
   }

   if(statusCode < 200 || statusCode >= 300)
   {
      Print("Bridge endpoint rejected the snapshot with HTTP ", statusCode, ".");
      return;
   }

   Print("Published read-only ", _Symbol, " snapshot at ", capturedAt, ".");
}

bool BuildTimeframeJson(
   const ENUM_TIMEFRAMES timeframe,
   const string label,
   string &json
)
{
   int shift = InpUseClosedBar ? 1 : 0;
   double close = iClose(_Symbol, timeframe, shift);
   double ema5;
   double ma9;
   double ma21;
   double ma63;
   double ma84;
   double bbUpper;
   double bbLower;
   double rsi;
   double macdHistogram;

   if(close <= 0.0
      || !ReadMovingAverage(timeframe, 5, MODE_EMA, shift, ema5)
      || !ReadMovingAverage(timeframe, 9, MODE_SMA, shift, ma9)
      || !ReadMovingAverage(timeframe, 21, MODE_SMA, shift, ma21)
      || !ReadMovingAverage(timeframe, 63, MODE_SMA, shift, ma63)
      || !ReadMovingAverage(timeframe, 84, MODE_SMA, shift, ma84)
      || !ReadBollingerBands(timeframe, shift, bbUpper, bbLower)
      || !ReadRsi(timeframe, shift, rsi)
      || !ReadMacdHistogram(timeframe, shift, macdHistogram))
   {
      return(false);
   }

   json = "{"
      + "\"timeframe\":\"" + label + "\","
      + "\"close\":" + Number(close, _Digits) + ","
      + "\"ema5\":" + Number(ema5, _Digits) + ","
      + "\"ma9\":" + Number(ma9, _Digits) + ","
      + "\"ma21\":" + Number(ma21, _Digits) + ","
      + "\"ma63\":" + Number(ma63, _Digits) + ","
      + "\"ma84\":" + Number(ma84, _Digits) + ","
      + "\"bb_upper\":" + Number(bbUpper, _Digits) + ","
      + "\"bb_lower\":" + Number(bbLower, _Digits) + ","
      + "\"rsi\":" + Number(rsi, 4) + ","
      + "\"macd_histogram\":" + Number(macdHistogram, 8)
      + "}";
   return(true);
}

bool ReadMovingAverage(
   const ENUM_TIMEFRAMES timeframe,
   const int period,
   const ENUM_MA_METHOD method,
   const int shift,
   double &value
)
{
   int handle = iMA(_Symbol, timeframe, period, 0, method, PRICE_CLOSE);
   return(ReadSingleAndRelease(handle, 0, shift, value));
}

bool ReadBollingerBands(
   const ENUM_TIMEFRAMES timeframe,
   const int shift,
   double &upper,
   double &lower
)
{
   int handle = iBands(_Symbol, timeframe, 21, 0, 2.0, PRICE_CLOSE);
   if(handle == INVALID_HANDLE)
      return(false);

   bool success = ReadSingle(handle, 1, shift, upper)
      && ReadSingle(handle, 2, shift, lower);
   IndicatorRelease(handle);
   return(success);
}

bool ReadRsi(
   const ENUM_TIMEFRAMES timeframe,
   const int shift,
   double &value
)
{
   int handle = iRSI(_Symbol, timeframe, 14, PRICE_CLOSE);
   return(ReadSingleAndRelease(handle, 0, shift, value));
}

bool ReadMacdHistogram(
   const ENUM_TIMEFRAMES timeframe,
   const int shift,
   double &value
)
{
   int handle = iMACD(_Symbol, timeframe, 12, 26, 9, PRICE_CLOSE);
   if(handle == INVALID_HANDLE)
      return(false);

   double mainLine;
   double signalLine;
   bool success = ReadSingle(handle, 0, shift, mainLine)
      && ReadSingle(handle, 1, shift, signalLine);
   IndicatorRelease(handle);
   if(success)
      value = mainLine - signalLine;
   return(success);
}

bool ReadSingleAndRelease(
   const int handle,
   const int buffer,
   const int shift,
   double &value
)
{
   if(handle == INVALID_HANDLE)
      return(false);
   bool success = ReadSingle(handle, buffer, shift, value);
   IndicatorRelease(handle);
   return(success);
}

bool ReadSingle(
   const int handle,
   const int buffer,
   const int shift,
   double &value
)
{
   double values[1];
   if(CopyBuffer(handle, buffer, shift, 1, values) != 1)
      return(false);
   value = values[0];
   return(MathIsValidNumber(value) && value != EMPTY_VALUE);
}

string TimeToIso8601(const datetime value)
{
   string result = TimeToString(value, TIME_DATE | TIME_SECONDS);
   StringReplace(result, ".", "-");
   StringReplace(result, " ", "T");
   return(result + "Z");
}

string JsonEscape(string value)
{
   StringReplace(value, "\\", "\\\\");
   StringReplace(value, "\"", "\\\"");
   return(value);
}

string Number(const double value, const int digits)
{
   return(DoubleToString(value, digits));
}
