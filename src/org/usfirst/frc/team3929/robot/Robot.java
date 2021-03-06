//climber is 7 and 6

//Drive Train pwm ports: fl 0 ; fr 1; br 2; bl 4;
package org.usfirst.frc.team3929.robot;

import edu.wpi.first.wpilibj.*;
import edu.wpi.first.wpilibj.command.Command;
import edu.wpi.first.wpilibj.command.Scheduler;
import edu.wpi.first.wpilibj.livewindow.LiveWindow;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.networktables.*;

import edu.wpi.first.wpilibj.CameraServer;
import org.opencv.core.Mat;
import edu.wpi.cscore.CvSink;
import edu.wpi.cscore.CvSource;
import edu.wpi.cscore.UsbCamera;

import com.kauailabs.navx_mxp.AHRS;

/**
 * The VM is configured to automatically run this class, and to call the
 * functions corresponding to each mode, as described in the IterativeRobot
 * documentation. If you change the name of this class or the package after
 * creating this project, you must also update the manifest file in the resource
 * directory.
 */
public class Robot extends IterativeRobot {

	boolean feederAuton = false;
	public enum AutoState {
		START, GO, TURN, ALIGN, PIDALIGN, PLACE, FEEDER, RUNNER, FINISH, DUMMY;
	}
	public enum TurnDirection{
		LEFT, RIGHT, STRAIGHT
	}

	AutoState CurrentAutoState = AutoState.START;
	
	TurnDirection Starter = TurnDirection.LEFT;
	
	Boolean shoot = false;

	Command autonomousCommand;
	SendableChooser<Command> chooser = new SendableChooser<>();
	//.008 is a okay so is .07
	final double kPgyro = 0.05;
	final double kIgyro = 0.0;
	final double kDgyro = 0.0;
	final double MAX_ROTATION_INPUT = .6;
	final double MINIMUM_ROTATION_INPUT = .4;
	final double MAX_VISION_INPUT = 0.5;
	
	PIDTool pidGyro, pidVision;

	//DRIVE TRAIN SPEED CONTROLLERS
	VictorSP fL;
	VictorSP fR;
	VictorSP bL;
	VictorSP bR;
	VictorSP c1;
	VictorSP c2;
	
	
	//DRIVE TRAIN ENCODERS
	Encoder leftEncoder, rightEncoder;
	int leftCount, rightCount;
	double driveDistance, dpp;
	
	double offsetFactor;
	
	//DRIVE JOYS
	Joystick joy;
	Joystick opjoy;
	Joystick haroldsjoystick;
	DigitalInput lim;
	RobotDrive drive;
	VictorSP leadScrew;
	
	//MECHANISMS
	DoubleSolenoid gearPiss;
	DoubleSolenoid lidPiss;
	
	CameraServer server;

	boolean zeroed;
	//VISION
	NetworkTable table;
	boolean found;
	String offset;
	double distance, center;
	boolean capturing;
	boolean straight;
	

	
	//AUTON
	Timer timer;
	double autoLeft, autoRight;
	int correctingCheck;
	double autonDrive;
	
	SendableChooser autoChooser;
	
	float drivePower;
	double rightDToffset;
	double rightTeleopOffset;
	double leftTeleopOffset;
	
	double testTime = 1;
	
	SerialPort serial_port;
	// IMU imu; // This class can be used w/nav6 and navX MXP.
	// IMUAdvanced imu; // This class can be used w/nav6 and navX MXP.
	AHRS imu; // This class can only be used w/the navX MXP.
	
	boolean zeroGyro;
	
	boolean realign;
	
	double leftDrive, rightDrive;
	
	PowerDistributionPanel pdp = new PowerDistributionPanel();

	/**
	 * This function is run when the robot is first started up and should be
	 * used for any initialization code.
	 */
	@Override
	public void robotInit() {
		fL = new VictorSP(0);
		bL = new VictorSP(3);
		fR = new VictorSP(1);
		bR = new VictorSP(2);
		c1 = new VictorSP(7);
		c2 = new VictorSP(6);
		
		joy = new Joystick(0);
		opjoy = new Joystick(1);
		
		
		
		//this code gives an error : ERROR: bind() to port 1735 failed: Address already in use (TCPAcceptor.cpp:108)
//.95
		//offsetFactor = 1.00; 
		straight = true;
		// haroldsjoystick = new Joystick(1);
		// haroldsmotor = new VictorSP(4);
		drive = new RobotDrive(fL, bL, fR, bR);
		// chooser.addObject("My Auto", new MyAutoCommand());
		SmartDashboard.putData("Auto mode", chooser);
		drivePower = 0.25f; //.4f
		lidPiss = new DoubleSolenoid(4, 5);
		gearPiss = new DoubleSolenoid(2, 3);
		server = CameraServer.getInstance();
		
		rightEncoder = new Encoder(0, 1, false, Encoder.EncodingType.k4X);
		leftEncoder = new Encoder(2, 3, false, Encoder.EncodingType.k4X);
		
		zeroGyro = true;
		pidGyro = new PIDTool(kPgyro, kIgyro, kDgyro, 0, -MAX_ROTATION_INPUT, MAX_ROTATION_INPUT);
		pidVision = new PIDTool(kPgyro, kIgyro, kDgyro, 0, -MAX_VISION_INPUT, MAX_VISION_INPUT);       
		//etworkTable.setIPAddress("10.39.29.25");
       // table = NetworkTable.getTable("VisionTable");
        
        //measured in inches. 3592 pulse average between -3897 left and 3287 right
		//1.01
		rightDToffset = 1.04;
		//rightTeleopOffset = 1.45;
		rightTeleopOffset = 1.50;
		leftTeleopOffset = 1.0;
		dpp = 12.64;
		//12.54
		//allowCamFront = true;

		try {

			// Use SerialPort.Port.kOnboard if connecting nav6 to Roborio Rs-232
			// port
			// Use SerialPort.Port.kMXP if connecting navX MXP to the RoboRio
			// MXP port
			// Use SerialPort.Port.kUSB if connecting nav6 or navX MXP to the
			// RoboRio USB port

			serial_port = new SerialPort(57600, SerialPort.Port.kMXP);

			// You can add a second parameter to modify the
			// update rate (in hz) from. The minimum is 4.
			// The maximum (and the default) is 100 on a nav6, 60 on a navX MXP.
			// If you need to minimize CPU load, you can set it to a
			// lower value, as shown here, depending upon your needs.
			// The recommended maximum update rate is 50Hz

			// You can also use the IMUAdvanced class for advanced
			// features on a nav6 or a navX MXP.

			// You can also use the AHRS class for advanced features on
			// a navX MXP. This offers superior performance to the
			// IMU Advanced class, and also access to 9-axis headings
			// and magnetic disturbance detection. This class also offers
			// access to altitude/barometric pressure data from a
			// navX MXP Aero.

			byte update_rate_hz = 50;
			// imu = new IMU(serial_port,update_rate_hz);
			// imu = new IMUAdvanced(serial_port,update_rate_hz);
			imu = new AHRS(serial_port, update_rate_hz);
		} catch (Exception ex) {

		}
		UsbCamera cam0 = CameraServer.getInstance().startAutomaticCapture(0);
		//UsbCamera cam1 = CameraServer.getInstance().startAutomaticCapture(1);

		

	}

	/**
	 * This function is called once each time the robot enters Disabled mode.
	 * You can use it to reset any subsystem information you want to clear when
	 * the robot is disabled.
	 */
	@Override
	public void disabledInit() {

	}

	@Override
	public void disabledPeriodic() {
		Scheduler.getInstance().run();
	}

	/**
	 * This autonomous (along with the chooser code above) shows how to select
	 * between different autonomous modes using the dashboard. The sendable
	 * chooser code works with the Java SmartDashboard. If you prefer the
	 * LabVIEW Dashboard, remove all of the chooser code and uncomment the
	 * getString code to get the auto name from the text box below the Gyro
	 *
	 * You can add additional auto modes by adding additional commands to the
	 * chooser code above (like the commented example) or additional comparisons
	 * to the switch structure below with additional strings & commands.
	 */
	@Override
	public void autonomousInit() {
		autonomousCommand = chooser.getSelected();
		
		CurrentAutoState = AutoState.START;

		/*
		 * String autoSelected = SmartDashboard.getString("Auto Selector",
		 * "Default"); switch(autoSelected) { case "My Auto": autonomousCommand
		 * = new MyAutoCommand(); break; case "Default Auto": default:
		 * autonomousCommand = new ExampleCommand(); break; }
		 */

		// schedule the autonomous command (example)
		if (autonomousCommand != null)
			autonomousCommand.start();
		
		//resetting autonomous parameters
		imu.zeroYaw();
		System.out.println("Gyro Reset");
		resetEncoders();
		pidGyro.setSetpoint(0.0);
		pidVision.setSetpoint(0.0);
		realign = true;
		
		found = false;
		offset = "default";
		distance = 100;
		capturing = false;
		center = 10;
		
		//get rid of this after pid tuning

	}

	/**
	 * This function is called periodically during autonomous
	 */
	
	//Overestimating distance by 2 inches
	@Override
	public void autonomousPeriodic() {
		
/*		if(SmartDashboard.getBoolean("DB/Button 0",false)){
			Starter = TurnDirection.RIGHT;
		} else if(!SmartDashboard.getBoolean("DB/Button 0",false)){
			Starter = TurnDirection.LEFT;
		}if(SmartDashboard.getBoolean("DB/Button 1", false)){
			Starter = TurnDirection.STRAIGHT;
		}*/ 
		System.out.println("Starter: " + Starter);
		Scheduler.getInstance().run();
		//lidPiss.set(DoubleSolenoid.Value.kForward);

		switch(CurrentAutoState) {
		case START:
			CurrentAutoState = AutoState.GO;
			break;
		case GO:
			//robot is 36 inches
			// right was 24
			gearPiss.set(DoubleSolenoid.Value.kForward);
	
			if(Starter == TurnDirection.RIGHT)
				//87 inches b4 1:54 pm//78..5
				autonDrive = 71.5;
			else if(Starter == TurnDirection.LEFT)
				//82 b4 1:54pm; 41
				autonDrive = 67.5;
			else if(Starter == TurnDirection.STRAIGHT){
				//22.5
				autonDrive = 48;
			}
			getEncoders();
			
				if((driveDistance ) <= autonDrive){
				autoLeft = pidGyro.computeControl(0) + 0.55;
				autoRight = (pidGyro.computeControl(0) + 0.55)*rightDToffset;
/*				if(timer.get() < 3000){
					
				}*/
			} else {
				autoRight = 0.0;
				autoLeft = 0.0;
				resetEncoders();
				CurrentAutoState = AutoState.TURN;
			
			}
			break;
		case TURN:
			if(Starter == TurnDirection.RIGHT){
				autoLeft = 0.45;
				autoRight = -(0.45 * rightDToffset);
				}
				else if(Starter == TurnDirection.LEFT){
					autoLeft = -0.45;
					autoRight = (0.45 * dpp);
				}
				else if(Starter == TurnDirection.STRAIGHT){
					autoLeft = 0;
					autoRight = 0;
					CurrentAutoState = AutoState.PLACE;
				}
			if(Starter == TurnDirection.RIGHT){
				//49,45
				if((imu.getYaw() > 49) || (imu.getYaw() < 45)){
					autoLeft = pidGyro.computeControl(47);
					autoRight = -(pidGyro.computeControl(47));
					}
				else{
					autoLeft = 0.0;
					autoRight = 0.0;
					resetEncoders();
					//timer.start();
					CurrentAutoState = AutoState.PLACE;
				}
			}
			else if(Starter == TurnDirection.LEFT){
				//-47, 43, 47
				if((imu.getYaw() < -48) || (imu.getYaw() > -44)){
					autoLeft = pidGyro.computeControl(-46);
					autoRight = -pidGyro.computeControl(-46);
				}
				else{
					autoLeft = 0.0;
					autoRight = 0.0;
					resetEncoders();
					//timer.start();
					CurrentAutoState = AutoState.PLACE;
				}
					
			}
			else{
				//timer.start();
				CurrentAutoState = AutoState.PLACE;
			}
			break;
/*		case ALIGN:
			System.out.println(offset);
			if(found){
				System.out.println("Tape Found? " + found);
				if(offset.equals("left")){
					System.out.println("Offset: " + offset);
					autoLeft = -0.48;
					autoRight = 0.48 * rightDToffset;
					
				} else if(offset.equals("right")){
					System.out.println("Offset: " + offset);
					autoRight = -(0.48 * rightDToffset);
					autoLeft = 0.48;
				} else if(offset.equals("centered")){
					autoRight = 0.0;
					autoLeft = 0.0;
					resetEncoders();
					CurrentAutoState = AutoState.PLACE;
				} 
			} else {
				if(realign && autoRight > 0.0){
					autoLeft = 0.5;
					autoRight = -0.55;
					realign = false;
				}else if(realign && autoLeft > 0.0){
					autoLeft = -0.5;
					autoRight = 0.55;
					realign = false;
				}
			}
				break;
		case PIDALIGN:
			System.out.println(offset);
			if(found){
				autoLeft = pidVision.computeControl(center);
				autoRight = pidVision.computeControl(center);
				if(center<5 && -5<center){
					CurrentAutoState = AutoState.FINISH;
				}
			} else {
				if(realign && autoRight > 0.0){
					autoLeft = 0.5;
					autoRight = -(0.5 * rightDToffset);
					realign = false;
				}else if(realign && autoLeft > 0.0){
					autoLeft = -0.5;
					autoRight = 0.5 * rightDToffset;
					realign = false;
				}
			}
				break;*/
		case PLACE:
			getEncoders();
			if(Starter != TurnDirection.STRAIGHT){
				if(driveDistance <= 10 && Starter == TurnDirection.LEFT){
					autoLeft = 0.5;
					autoRight = 0.5 * rightDToffset;
/*					if(timer.get()>=3.0){
						
						if(Math.abs(driveDistance) <=4.0){
						autoLeft = -0.5;
						autoRight = -0.5 * rightDToffset;
						} else {
							autoLeft = 0.0;
							autoRight = 0.0;
							gearPiss.set(DoubleSolenoid.Value.kReverse);
							Timer.delay(1);
							resetEncoders();

							CurrentAutoState = AutoState.FINISH;
						}
					}*/
				}
				else if(driveDistance <= 5.0 && Starter == TurnDirection.RIGHT){
					autoLeft = 0.5;
					autoRight = 0.5 * rightDToffset;
				}

				else{autoLeft = 0.0;
				autoRight = 0.0;
				Timer.delay(1.0);
				if(shoot)
					gearPiss.set(DoubleSolenoid.Value.kReverse);
				Timer.delay(1.5);
				resetEncoders();

				CurrentAutoState = AutoState.FINISH;
				}
			}
				else{
				autoLeft = 0.0;
				autoRight = 0.0;
				Timer.delay(1.0);
				if(shoot)
					gearPiss.set(DoubleSolenoid.Value.kReverse);
				Timer.delay(1);
				resetEncoders();
				CurrentAutoState = AutoState.FINISH;
				}
			break;
		case FINISH:
			gearPiss.set(DoubleSolenoid.Value.kForward);
			getEncoders();
			if((driveDistance >= -10) && shoot){
				autoLeft = -0.5;
				autoRight = -0.5   * rightDToffset;
			}
			else {
				autoLeft = 0.0;
				autoRight = 0.0;
				if(Starter != TurnDirection.STRAIGHT){
					if(imu.getYaw() > 2 || imu.getYaw() < -2){
						autoLeft = pidGyro.computeControl(0);
						autoRight = pidGyro.computeControl(0);
					}

			}
			
			}
			if(feederAuton){
				resetEncoders();
				pidGyro.setSetpoint(0.0);
				CurrentAutoState = AutoState.FEEDER;
			}
			break;
		case FEEDER:
			if(!(imu.getYaw() > -2) && !(imu.getYaw() < 2)){
				autoLeft = pidGyro.computeControl(imu.getYaw());
				autoRight = -pidGyro.computeControl(imu.getYaw());
			}
			else{
				Timer.delay(1);
				resetEncoders();
				CurrentAutoState = AutoState.RUNNER;
			}
			break;
		case RUNNER:
			//autoLeft = 0;
			//autoRight = 0;
			//Timer.delay(1);
			autoLeft = 0.9;
			autoRight = 0.9;
			if(driveDistance > 50){
				autoLeft = 0;
				autoRight = 0;
//					CurrentAutoState = AutoState.FINISH;
			}
			break;
		case DUMMY:
			break;
			}
		drive.tankDrive(autoLeft, autoRight);
		//System.out.println("Current: " + CurrentAutoState);
		//System.out.println("distance "+ Math.abs(driveDistance));
		SmartDashboard.putString("DB/String 9", Integer.toString(rightEncoder.get()));
		SmartDashboard.putString("DB/String 2", "Encoder Distance: " + driveDistance);
		SmartDashboard.putString("DB/String 1", "Current Autonomous State: " + CurrentAutoState);
		SmartDashboard.putString("DB/String 3", "Gyro Angle: " + imu.getYaw());
		SmartDashboard.putString("DB/String 4", "Found: " + found);
		SmartDashboard.putString("DB/String 5", "Offset: " + offset);
		SmartDashboard.putString("DB/String 6", "DistanceA: " + distance);
		SmartDashboard.putString("DB/String 8", "State" + CurrentAutoState);
		
		System.out.println("R Encoder "+rightEncoder.get());
		System.out.println("L Encoder "+leftEncoder.get());
		//drive.mecanumDrive_Polar(0, 0, pidGyro.computeControl(imu.getYaw()));
		//drive.tankDrive(-pidGyro.computeControl(imu.getYaw()), pidGyro.computeControl(imu.getYaw()));
		//System.out.println(pidGyro.computeControl(imu.getYaw()));
		
		//System.out.println(driveDistance);
	}
	

	@Override
	public void teleopInit() {
		// This makes sure that the autonomous stops running when
		// teleop starts running. If you want the autonomous to
		// continue until interrupted by another command, remove
		// this line or comment it out.
		if (autonomousCommand != null)
			autonomousCommand.cancel();
		
		pidGyro.setSetpoint(0.0);
		resetEncoders();
		CurrentAutoState = AutoState.START;
		 leftDrive = 0.0;
		 rightDrive = 0.0;
		
	}

	/**
	 * This function is called periodically during operator control
	 */
	@Override
	public void teleopPeriodic() {
		System.out.println("Button: " + SmartDashboard.getBoolean("DB/Button 0",false));
		rightDrive = -joy.getRawAxis(3);
		if(-joy.getRawAxis(1) <= .3 && -joy.getRawAxis(1) >=-.3) {
			leftDrive = 0.0;
		}
		else{
			leftDrive = -joy.getRawAxis(1);
		}
		//turbo
		if(joy.getRawButton(5)){
			drivePower = 1;
		} else if(joy.getRawButton(8)){
			drivePower = 0.5f;
		} else{
			drivePower=.8f;
		}
		Scheduler.getInstance().run();
		
		drive.tankDrive(((leftDrive * leftTeleopOffset) * drivePower), (rightDrive * 1) * drivePower);
		
		
		System.out.println(fR.get());
		System.out.println(fL.get());
		//Straight driving
		if (joy.getRawButton(6)) {
			if(zeroGyro){
				imu.zeroYaw();
				zeroGyro = false;
			}
			drive.tankDrive((rightDrive - pidGyro.computeControl(imu.getYaw())) * drivePower, rightDrive * drivePower);
			
			
		}

		
		//Backwards driving
		if (joy.getRawButton(3)) {
			drive.tankDrive((leftDrive * offsetFactor) * drivePower, rightDrive * drivePower);
			
			//Backwards and Straight
			if (joy.getRawButton(6)) {
				if(zeroGyro){
					imu.zeroYaw();
					zeroGyro = false;
				}
				drive.tankDrive((leftDrive * offsetFactor) * drivePower, leftDrive * drivePower);
			}
		}
		
		
		//Pneumatics actuating
		if (opjoy.getRawButton(5)) {
			lidPiss.set(DoubleSolenoid.Value.kForward);
		} else if (opjoy.getRawButton(6)) {
			lidPiss.set(DoubleSolenoid.Value.kReverse);
		} else {
			lidPiss.set(DoubleSolenoid.Value.kOff);
		}

		if (opjoy.getRawButton(2)) {
			gearPiss.set(DoubleSolenoid.Value.kReverse);
			Timer.delay(.75);
			gearPiss.set(DoubleSolenoid.Value.kForward);
		}
		
		//Climber controls
/*		if(opjoy.getRawButton(3)){
			c1.set(0.9);
			c2.set(0.9);
		}*/
		
		if(opjoy.getRawButton(3)) {
/*			if(0.5 > opjoy.getRawAxis(1) && opjoy.getRawAxis(1) > 0){
			c1.set(0.9 * (1.1 - opjoy.getRawAxis(1))); 
			c2.set(0.9 * (1.1 - opjoy.getRawAxis(1)));
			}
			else if(opjoy.getRawAxis(1) >= 0.5){
			c1.set(0.5);
			c2.set(0.5);
			}
			else{
			c1.set(0.9);
			c2.set(0.9);
			}*/
			c1.set(1.0);
			c2.set(1.0);
		} else {
			c1.set(0); 
			c2.set(0);
		}
		
		
		//gearPiss.set(DoubleSolenoid.Value.kForward);
	
		

		testTime++;

		System.out.println("Left Joy: " + joy.getRawAxis(1) + "Right Joy: " + joy.getRawAxis(5));
		if(joy.getRawButton(1)){
			resetEncoders();
		} if(joy.getRawButton(2)){
			imu.zeroYaw();
		}
		
		
		
		SmartDashboard.putString("DB/String 1", "Left Encoder");
		SmartDashboard.putString("DB/String 6", Double.toString(-leftEncoder.get()));
		
		SmartDashboard.putString("DB/String 2", "Right Encoder");
		SmartDashboard.putString("DB/String 7", Double.toString(rightEncoder.get()));
		
		SmartDashboard.putString("DB/String 5",  "Distance" + driveDistance);
		

		SmartDashboard.putString("DB/String 3", "Gyro Angle");
		SmartDashboard.putString("DB/String 8", Double.toString(imu.getYaw()));
		System.out.println("R Encoder "+rightEncoder.get());
		System.out.println("L Encoder "+leftEncoder.get());
		System.out.println("Gyro: " + Double.toString(imu.getYaw()));

/*		
		System.out.println("Current 0 " + pdp.getCurrent(0));
		System.out.println("Current 1 " + pdp.getCurrent(1));
		System.out.println("Current 2 " + pdp.getCurrent(2));
		System.out.println("Current 12 " + pdp.getCurrent(12));*/
		//System.out.println("Distance" + distance );
	}

	/**
	 * This function is called periodically during test mode
	 */
	@Override
	public void testPeriodic() {
		LiveWindow.run();

	}
	public void getEncoders(){
		leftCount = -leftEncoder.get();
		rightCount = rightEncoder.get();
		
		driveDistance = ((leftCount + rightCount)/2)/dpp;
		//driveDistance = ((rightCount))/dpp;

	}
	public void resetEncoders(){
		leftCount = 0;
		rightCount = 0;
		driveDistance = 0;
		
		leftEncoder.reset();
		rightEncoder.reset();
	}
	public void startCameras(){
        

		
	}

}