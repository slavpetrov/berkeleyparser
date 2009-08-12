package edu.berkeley.nlp.speech.features;

import java.util.Arrays;

public class FastFourierTransform {
	int N;
	int logN;
	double[] W_re;
	double[] W_im;
	double[] re_copy;
	double[] im_copy;
	int[] permute;

	public FastFourierTransform(int N) {
		this.N = N;
		this.logN = (int) (Math.log(N) / Math.log(2));
		createW();
		createPermute();
	}

	private void createW() {
		W_re = new double[N/2];
		W_im = new double[N/2];
		for (int i=0; i < N/2; ++i) {
			int br = reverseBits(i,logN-1);
			W_re[br] = Math.cos(((double)i*2.0*Math.PI)/((double)N));
			W_im[br] = Math.sin(((double)i*2.0*Math.PI)/((double)N));
			re_copy = new double[N];
			im_copy = new double[N];
		}
	}

	private static int reverseBits(int index, final int numBits) {
		int i;
		int rev;
		for (i = rev = 0; i < numBits; i++) {
			rev = (rev << 1) | (index & 1);
			index >>= 1;
		}
		return rev;
	}

	public void transform(double[] re, double[] im) {		
		for (int m=N; m>=2; m=m>>1)  { // For each butterfly level
			int mt = m >> 1;
			for (int g=0,k=0; g< N; g+=m,k++) { // For each group
				   double w_re = W_re[k];           
				   double w_im = W_im[k];
				   for (int b=g; b<(g+mt); b++) {
				  	 		// t = w * x[b+mt]
				        double t_re = w_re * re[b+mt] - w_im * im[b+mt];
				        double t_im = w_re * im[b+mt] + w_im * re[b+mt];
				        double u_re = re[b];
				        double u_im = im[b];
				        // x[b] = u + t
				        re[b] = u_re + t_re;
				        im[b] = u_im + t_im;
				        // x[b+mt] = u - t
				        re[b+mt] = u_re - t_re;
				        im[b+mt] = u_im - t_im;
				   }
			}
		}
		permuteInPlace(re, im);
		// Have to conjugate result
		for (int i=0; i < N; ++i) { im[i] *= -1; }
	}

	private void permuteInPlace(double[] re, double[] im) {
		System.arraycopy(re, 0, re_copy, 0, N);
		System.arraycopy(im, 0, im_copy, 0, N);

		for (int i=0; i < N; ++i) {
			int p = permute[i];
			re[i] = re_copy[p];
			im[i] = im_copy[p];
		}
	}

	private void createPermute() {
		permute = new int[N];
		for (int i=0; i < N; ++i) {
			permute[i] = reverseBits(i, logN);
		}
	}

	public static void main(String[] args) {
		double[] re = {1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0};
		double[] im = {0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0};
		FastFourierTransform fft = new FastFourierTransform(8);
		fft.transform(re, im);
		System.out.printf("re: %s im: %s\n",Arrays.toString(re), Arrays.toString(im));
	}
}
