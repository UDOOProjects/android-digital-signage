#include "variant.h"
#include <stdio.h>
#include <adk.h>

#define  LED_PIN  53

// Accessory descriptor. It's how Arduino identifies itself to Android.
char descriptionName[]  = "UDOOadk_gestureSlider"; 
char modelName[]        = "DigitalSignage"; 
char manufacturerName[] = "Aidilab";  
char versionNumber[]    = "1.0"; 
char serialNumber[]     = "1";
char url[]              = "http://www.udoo.org/‎"; 

USBHost Usb;
ADK adk(&Usb, manufacturerName, modelName, descriptionName, versionNumber, url, serialNumber);


int LEFT     = 6;
int RIGHT    = 3;

int REDPIN   = 8;
int GREENPIN = 9;
int BLUEPIN  = 10;

int TH_ON    = 50;

int cicliDoppiaMano = 0;
int DOPPIAMANO_TH   = 10;

boolean R, L;
int rValue, lValue;
int stato    = 0;

int START_LED_VALUE= 400;

int ledValue = START_LED_VALUE;

int Red   = 255;
int Green = 100;
int Blue  = 100;

int HIGHEST = 700;
int LOWEST  = 30;


void setup() {
    // set communiation speed
    Serial.begin(9600);   
    pinMode(LED_PIN, OUTPUT);
    
    pinMode(REDPIN, OUTPUT);
    pinMode(GREENPIN, OUTPUT);
    pinMode(BLUEPIN, OUTPUT);
    
    digitalWrite(LED_PIN,LOW);
    cpu_irq_enable();   // ??
    printf("\r\nUDOO Digital Signage\r\n");
    delay(200);

    analogWrite(REDPIN,   255 - Red);
    analogWrite(GREENPIN, 255 - Green);
    analogWrite(BLUEPIN,  255 - Blue);
}

#define RCVSIZE 128

void loop() {
    
  Usb.Task();
  
  readADK();
  
  delay(100);
  rValue = analogRead(RIGHT);
  lValue = analogRead(LEFT);

  R = L = false;

  if(rValue > TH_ON){
    R = true;
  }

  if(lValue > TH_ON){
    L = true;
  }

  if(L && R ){   
    cicliDoppiaMano++;
  }
  else{
    cicliDoppiaMano = 0;
  }
  
  if(cicliDoppiaMano == DOPPIAMANO_TH){      
    stato = 8;
    writeADK(5);
  }  

}
 
 
 
 
 
  // controllo stati swipe
  if(stato == 0){
    if(!R && !L)     stato = 0;
    else if(!R && L) stato = 1;
    else if(R && !L) stato = 2;
    else             stato = 7;   
  }
  else if(stato == 1){
    if(!R && !L)     stato = 0;
    else if(!R && L) stato = 1;
    else if(R && !L) stato = 5;
    else             stato = 3;   
  }  
  else if(stato == 2){
    if(!R && !L)     stato = 0;
    else if(!R && L) stato = 6;
    else if(R && !L) stato = 2;
    else             stato = 4;   
  }  
  else if(stato == 3){
    if(!R && !L)     stato = 0;
    else if(!R && L) stato = 7;
    else if(R && !L) stato = 5;
    else             stato = 3;   
  }  
  else if(stato == 4){
    if(!R && !L)     stato = 0;
    else if(!R && L) stato = 6;
    else if(R && !L) stato = 7;
    else             stato = 4;   
  }  
  else if(stato == 5){    
    writeADK(1);
    if(!R && !L)     stato = 0;
    else if(!R && L) stato = 7;
    else if(R && !L) stato = 7;
    else             stato = 7;
 
    ledValue = START_LED_VALUE;    
  }  
  else if(stato == 6){   
    writeADK(2);
    if(!R && !L)     stato = 0;
    else if(!R && L) stato = 7;
    else if(R && !L) stato = 7;
    else             stato = 7;       
    ledValue = START_LED_VALUE;
  }  
  else if(stato == 7){
    if(!R && !L)     stato = 0;
    else if(!R && L) stato = 7;
    else if(R && !L) stato = 7;
    else             stato = 7;   
    
    manoDestraIn = false;
    manoDestraMin = 10000;
    raggiuntoTopDestra = false;
    
    manoSinistraIn = false;
    manoSinistraMin = 10000;
  }  
  
  else if(stato == 8){ // doppia  
    if(!R && !L){
      stato = 7;
    }
    else
      ledValue = (rValue + lValue) >> 1;  

  }  
  
 
  
  playColor();
  
  delay(10);
}



void playColor(){  
  int dimm = 255;   
  dimm = map(ledValue, HIGHEST, LOWEST, 0, 255);
 
  if(ledValue > HIGHEST)
    ledValue = HIGHEST;
  else if(ledValue < LOWEST)
    ledValue = LOWEST; 
    
  if(dimm > 255)
    dimm = 255;
  else if(dimm < 0)
    dimm = 0; 
  
  int r = map(Red, 0, 255, 0, dimm);
  int g = map(Green, 0, 255, 0, dimm);
  int b = map(Blue, 0, 255, 0, dimm);
  
    
  analogWrite(REDPIN,   255 - (int) r);
  analogWrite(GREENPIN, 255 - (int) g);
  analogWrite(BLUEPIN,  255 - (int) b);
  
}


void readADK()
{       
    uint8_t buf[RCVSIZE];
    uint32_t nbread = 0;
  
    if (adk.isReady()) {
      adk.read(&nbread, RCVSIZE, buf);// read data into buf variable
      if (nbread > 0) {
         Red   = buf[0];
         Green = buf[1];
         Blue  = buf[2]; 
      }
    } 
    else{
    
    }
}

void writeADK(int c){  
  if (adk.isReady()){
    uint8_t bufWrite[1];
    bufWrite[0] = (uint8_t)c;
    adk.write(sizeof(bufWrite), (uint8_t *)bufWrite);
  }
}



void driveOnLed(){
  
  int i= 0;
  for(i=0; i<10; i++){
    int dimm = 255;   
    dimm = map(ledValue, HIGHEST, LOWEST, 0, 255);
   
    if(ledValue > HIGHEST)
      ledValue = HIGHEST;
    else if(ledValue < LOWEST)
      ledValue = LOWEST; 
      
    if(dimm > 255)
      dimm = 255;
    else if(dimm < 0)
      dimm = 0; 
    
    int r = map(Red, 0, 255, 0, dimm);
    int g = map(Green, 0, 255, 0, dimm);
    int b = map(Blue, 0, 255, 0, dimm);
    
      
    analogWrite(REDPIN,   255 - (int) r);
    analogWrite(GREENPIN, 255 - (int) g);
    analogWrite(BLUEPIN,  255 - (int) b);
  }

}
