package com.thiagomv.timecontroler;

/**
 * Esta interface define os eventos lan�ados por {@link PeriodicTimeController}.
 * 
 * @author Thiago Mendes Vieira - thiagomv.0301.developer@gmail.com
 * @version 1.0
 */
public interface PeriodicTimeEventListener {
	/**
	 * Evento acionado quando o tempo de ciclo de 1 frame for alcan�ado.
	 * 
	 * @param elapsedNanoseconds
	 *            Tempo decorrido desde a �ltima gera��o deste evento, em
	 *            nanosegundos.
	 */
	void onTimeEvent(double elapsedNanoseconds);

	/**
	 * Evento acionado quando o tempo de atrazo for igual ou superior ao ciclo
	 * de 1 frame.
	 * 
	 * @param numFrames
	 *            Quantidade de ciclos de frames perdidos no atrazo.
	 */
	void onFramesLost(int numFrames);
}