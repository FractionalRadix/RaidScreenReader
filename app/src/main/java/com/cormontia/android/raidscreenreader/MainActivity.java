package com.cormontia.android.raidscreenreader;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.IOException;

//TODO!+
// * Extract the raid boss name better. Right now it includes part of the CP.
// Nice-to-have features:
// * Give a warning if there is not enough memory to install Mobile Vision.
// * Right now only functions properly in portrait orientation. Make it work properly in landscape orientation, too.
// * Check for strings containing "CP"  or a raid boss name. The absence of these also indicates an egg rather than a boss. (Robusntness, multiple reasons to assume it's a raid screen).
// * Adding a list of known Pokemon (up to and including gen 4), to compare with the extracted Raid Boss name.
// * Add a list of known gyms and their locations, so we can enter the GPS coordinates automatically.
// * Check for "Walk closer to interact with this gym". IF there is NO box at all there, but there is a text "BATTLE", then the plaer is near the gym.
// * Check for the word "IV". This happens when a user has an unpaid version of Calcy IV. Calcy IV's overlay can wreak havoc with our image analysis.
// * Extract the gym color (not present as text)
// * For an unhatched raid boss, extract the number of stars.

public class MainActivity extends AppCompatActivity
{
    // We seek to extract the following information:
    // * Gym name
    //    (This implies location)
    // * Whether the raid boss has hatched or still in his/her egg.
    // * If the boss has hatched
    //      - what raid boss it is
    //      - how long (s)he will stay
    // * If the raid boss is still in his/her egg
    //      - how long until the egg hatches
    class RaidInfo
    {
        // Dear Java, why can't  you have auto-properties like C# ...

        private String gymName;
        public void setGymName(String gymName) { this.gymName = gymName; }
        public String getGymName() { return gymName; }

        private boolean hasHatched;
        public void setHasHatched( boolean flag ) { this.hasHatched = flag; }
        public boolean getHasHatched( ) { return this.hasHatched; }

        /** The number of minutes left until hatching, or until the raid boss flies away.
         * We ignore the seconds for now.
         **/
        private int minutesLeft;
        public void setMinutesLeft(int minutesLeft) { this.minutesLeft = minutesLeft; }
        public int getMinutesLeft( ) { return minutesLeft; }

        private String raidBossName;
        public void setRaidBossName(String name){ this.raidBossName = name; }
        public String getRaidBossName( ) { return raidBossName; }

        @Override
        public String toString( )
        {
            String res1 = "Gym name: " + gymName;
            String res2 = "Hatched: " + ((hasHatched) ? "Yes" : "No");
            String res3 = "Minutes left until hatch/end: " + minutesLeft;
            String res4 = raidBossName;
            return res1 + "; " + res2 + "; " + res3 + (hasHatched ? "; " + raidBossName : "");
        }
    }

    private TextRecognizer textRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );

        Context context = getApplicationContext( );
        textRecognizer = new TextRecognizer.Builder( context ).build( );
        if (!textRecognizer.isOperational())
        {
            Toast.makeText( context, "Oops! Google Vision is not installed.", Toast.LENGTH_LONG ).show();
        }
        else
        {
            Intent intent = getIntent( );
            String action = intent.getAction( );
            String type = intent.getType( );
            Log.i( "GETIMG", "OnCreate Marker 1." );
            if ( Intent.ACTION_SEND.equals( action ) && type != null )
            {
                Log.i( "GETIMG", "OnCreate Marker 2." );
                if ( type.startsWith( "image/" ) )
                {
                    Log.i( "GETIMG", "OnCreate Marker 3." );
                    Uri imageUri = (Uri) intent.getParcelableExtra( Intent.EXTRA_STREAM );
                    Log.i( "GETIMG", "OnCreate Marker 4. URI==" + imageUri );

                    RaidInfo raidInfo = getRaidInfo(imageUri);

                    Toast.makeText( context, raidInfo.toString(), Toast.LENGTH_LONG ).show( );


                    if (raidInfo.getGymName() == null || raidInfo.getGymName().compareTo( "" ) == 0 )
                    {
                        Toast.makeText( context, "Could not find gym name. Is this really a raid screen?", Toast.LENGTH_LONG ).show();
                    }

                    //Calendar calendar = new GregorianCalendar( );
                    //int hours = calendar.get( Calendar.HOUR_OF_DAY );
                    //int minutes = calendar.get( Calendar.MINUTE );

                    finish();
                }
            }
        }
    }

    public RaidInfo getRaidInfo(Uri imageUri)
    {
        // Get the image, then apply TextRecognizer to it.
        RaidInfo raidInfo = new RaidInfo();
        try
        {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap( this.getContentResolver( ), imageUri );
            Frame outputFrame = new Frame
                    .Builder( )
                    .setBitmap( bitmap )
                    .build( );
            SparseArray<TextBlock> texts = textRecognizer.detect( outputFrame );
            for ( int i = 0; i < texts.size( ); i++ )
            {
                TextBlock textBlock = texts.get( i );
                if ( textBlock != null )
                {
                    String text = texts.get( i ).getValue( );
                    Rect boundingBox = texts.get( i ).getBoundingBox( );
                    Log.i( "GETIMG", "TEXT FOUND: " + text + " " + boundingBox.toString( ) );

                    // Assume the the gym name is between 130 and 195.
                    if ( boundingBox.top >= 130 && boundingBox.bottom <= 195 )
                    {
                        raidInfo.setGymName( text );
                    }

                    // Time left for a hatched Raid Boss is on 1152 - 1192. Let's add a little margin and make it 1140-1200
                    if ( boundingBox.top >= 1140 && boundingBox.bottom <= 1200 )
                    {
                        raidInfo.setHasHatched( true );
                        String[] time = text.split( ":" );
                        int minutes = -1; // Sentinel value
                        try
                        {
                            minutes = Integer.parseInt( time[1] );
                            raidInfo.setMinutesLeft( minutes );
                        } catch ( NumberFormatException exc )
                        {
                            //TODO!+ Handle illegible numbers...
                            Log.w( "GETIMG", "Hatched egg. Unable to extract number of minutes..." );
                        }
                    }

                    // Time left for an unhatched raid boss is on 389 - 451. Again, let's add a little margin. Make it 380 - 460.
                    if (boundingBox.top >= 380 && boundingBox.bottom <= 460 )
                    {
                        raidInfo.setHasHatched( false );
                        String[] time = text.split( ":" );
                        int minutes = -1; // Sentinel value
                        try
                        {
                            minutes = Integer.parseInt( time[1] );
                            raidInfo.setMinutesLeft( minutes );
                        } catch ( NumberFormatException exc )
                        {
                            //TODO!+ Handle illegible numbers...
                            Log.w( "GETIMG", "Unhatched egg. Unable to extract number of minutes..." );
                        }
                    }

                    // Raid boss name. Usually comes together with CP in 320 - 618.
                    // We SHOULD split it in lines, but for now we'll settle with a part of the CP as the name.
                    // Again, we add a little margin.
                    if (boundingBox.top >= 310 && boundingBox.bottom <= 630)
                    {
                        String raidBossName = text;
                        raidInfo.setRaidBossName(raidBossName);
                    }

                }
            }
        }
        catch ( IOException e )
        {
            Log.e( "GETIMG", "Problem getting bitmap from image... or problem turning Bitmap into frame." );
            e.printStackTrace( );
        }

        return raidInfo;
    }
}
