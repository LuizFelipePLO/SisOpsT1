// PUCRS - Escola Politécnica - Sistemas Operacionais
// Prof. Fernando Dotti
// Código fornecido como parte da solução do projeto de Sistemas Operacionais
//
// VM
//    HW = memória, cpu
//    SW = tratamento int e chamada de sistema
// Funcionalidades de carga, execução e dump de memória

// Versão 0 = Interrupções e Chamadas de Sistema
// Versão 1 = Gerente de Memória e Gerente de Processos

import java.util.*;

public class SistemaT1_v1 {

	// -------------------------------------------------------------------------------------------------------
	// --------------------- H A R D W A R E - definicoes de HW
	// ----------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// --------------------- M E M O R I A - definicoes de palavra de memoria,
	// memória ----------------------

	public class Memory {
		public int tamMem;    
        public Word[] m;                  // m representa a memória fisica:   um array de posicoes de memoria (word)
	
		public Memory(int size){
			tamMem = size;
		    m = new Word[tamMem];      
		    for (int i=0; i<tamMem; i++) { m[i] = new Word(Opcode.___,-1,-1,-1); };
		}
		
		public void dump(Word w) {        // funcoes de DUMP nao existem em hardware - colocadas aqui para facilidade
						System.out.print("[ "); 
						System.out.print(w.opc); System.out.print(", ");
						System.out.print(w.r1);  System.out.print(", ");
						System.out.print(w.r2);  System.out.print(", ");
						System.out.print(w.p);  System.out.println("  ] ");
		}
		public void dump(int ini, int fim) {
			for (int i = ini; i < fim; i++) {		
				System.out.print(i); System.out.print(":  ");  dump(m[i]);
			}
		}
    }

	// -------------------------------------------------------------------------------------------------------

	public class Word { // cada posicao da memoria tem uma instrucao (ou um dado)
		public int partNum;
		public Opcode opc; //
		public int r1; // indice do primeiro registrador da operacao (Rs ou Rd cfe opcode na tabela)
		public int r2; // indice do segundo registrador da operacao (Rc ou Rs cfe operacao)
		public int p; // parametro para instrucao (k ou A cfe operacao), ou o dado, se opcode = DADO

		public Word(Opcode _opc, int _r1, int _r2, int _p) { // vide definição da VM - colunas vermelhas da tabela
			opc = _opc;
			r1 = _r1;
			r2 = _r2;
			p = _p;
		}
	}

	// -------------------------------------------------------------------------------------------------------
	// --------------------- C P U - definicoes da CPU
	// -----------------------------------------------------

	public enum Opcode {
		DATA, ___, // se memoria nesta posicao tem um dado, usa DATA, se nao usada ee NULO ___
		JMP, JMPI, JMPIG, JMPIL, JMPIE, // desvios e parada
		JMPIM, JMPIGM, JMPILM, JMPIEM, STOP,
		JMPIGK, JMPILK, JMPIEK, JMPIGT,
		ADDI, SUBI, ADD, SUB, MULT, // matematicos
		LDI, LDD, STD, LDX, STX, MOVE, // movimentacao
		TRAP // chamada de sistema
	}

	public enum Interrupts { // possiveis interrupcoes que esta CPU gera
		noInterrupt, intEnderecoInvalido, intInstrucaoInvalida, intRegistradorInvalido, intOverflow, chamadaTrap,
		intSTOP;
	}

	public class CPU {
		private int maxInt; // valores maximo e minimo para inteiros nesta cpu
		private int minInt;

		private int tamParticao; 

		
		// característica do processador: contexto da CPU ...
		private int pc; // ... composto de program counter,
		private Word ir; // instruction register,
		private int[] reg; // registradores da CPU
		private Interrupts irpt; // durante instrucao, interrupcao pode ser sinalizada
		private int base; // base e limite de acesso na memoria
		private int limite; // por enquanto toda memoria pode ser acessada pelo processo rodando
							// ATE AQUI: contexto da CPU - tudo que precisa sobre o estado de um processo
							// para executa-lo
							// nas proximas versoes isto pode modificar

		private Memory mem; // mem tem funcoes de dump e o array m de memória 'fisica'
		private Word[] m; // CPU acessa MEMORIA, guarda referencia a 'm'. m nao muda. semre será um array
							// de palavras

		private InterruptHandling ih; // significa desvio para rotinas de tratamento de Int - se int ligada, desvia
		private SysCallHandling sysCall; // significa desvio para tratamento de chamadas de sistema - trap
		private boolean debug; // se true entao mostra cada instrucao em execucao

		

		public CPU(Memory _mem, int tamParticao, InterruptHandling _ih, SysCallHandling _sysCall, boolean _debug) { // ref a MEMORIA e
																									// interrupt handler
																									// passada na
																									// criacao da CPU
			maxInt = 32767; // capacidade de representacao modelada
			minInt = -32767; // se exceder deve gerar interrupcao de overflow
			mem = _mem; // usa mem para acessar funcoes auxiliares (dump)
			m = mem.m; // usa o atributo 'm' para acessar a memoria.
			reg = new int[10]; // aloca o espaço dos registradores - regs 8 e 9 usados somente para IO
			ih = _ih; // aponta para rotinas de tratamento de int
			sysCall = _sysCall; // aponta para rotinas de tratamento de chamadas de sistema
			debug = _debug; // se true, print da instrucao em execucao
		}

		private boolean legal(int e) { // todo acesso a memoria tem que ser verificado
			if ((e < minInt) || (e > maxInt)) {
				irpt = Interrupts.intOverflow;
				return false;
			}
			;
			return true;
		}

		private boolean enderecoValido(int end) { // se acessar endereço fora do limite
			if ((end < base) || (end > limite)) {
				irpt = Interrupts.intEnderecoInvalido;
				return false;
			}
			;
			return true;
		}

		private boolean registradorValido(int registrador) {// verifica registrador valido 0 a 7
			if (registrador < 0 || registrador > reg.length) {
				irpt = Interrupts.intRegistradorInvalido;
				return false;
			}
			;
			return true;
		}

		private boolean testOverflow(int v) { // toda operacao matematica deve avaliar se ocorre overflow
			if ((v < minInt) || (v > maxInt)) {
				irpt = Interrupts.intOverflow;
				return false;
			}
			;
			return true;
		}

		public void setContext(int _base, int _limite, int _pc) { // no futuro esta funcao vai ter que ser
			base = _base; // expandida para setar todo contexto de execucao,
			limite = _limite; // agora, setamos somente os registradores base,
			pc = _pc; // limite e pc (deve ser zero nesta versao)
			irpt = Interrupts.noInterrupt; // reset da interrupcao registrada
		}

		public void run() { // execucao da CPU supoe que o contexto da CPU, vide acima, esta devidamente
							// setado
			while (true) { // ciclo de instrucoes. acaba cfe instrucao, veja cada caso.
				// --------------------------------------------------------------------------------------------------
				// FETCH
				if (legal(pc)) { // pc valido
					ir = m[pc]; // <<<<<<<<<<<< busca posicao da memoria apontada por pc, guarda em ir
					if (debug) {
						System.out.print("                               pc: " + pc + "       exec: ");
						mem.dump(ir);
					}
					// --------------------------------------------------------------------------------------------------
					// EXECUTA INSTRUCAO NO ir
					switch (ir.opc) { // conforme o opcode (código de operação) executa

						// Instrucoes de Busca e Armazenamento em Memoria
						case LDI: // Rd <- k
							if (registradorValido(ir.r1) && testOverflow(ir.p)) {
								reg[ir.r1] = ir.p;
								pc++;
								break;
							} else
								break;

						case LDD: // Rd <- [A]
							if (registradorValido(ir.r1) && enderecoValido(ir.p) && testOverflow(m[ir.p].p)) {
								reg[ir.r1] = m[ir.p].p;
								pc++;
								break;
							} else
								break;

						case LDX: // RD <- [RS] // NOVA carga indireta
							if (registradorValido(ir.r1) && registradorValido(ir.r2) && enderecoValido(reg[ir.r2])
									&& testOverflow(m[reg[ir.r2]].p)) {
								reg[ir.r1] = m[reg[ir.r2]].p;
								pc++;
								break;
							} else
								break;

						case STD: // [A] ← Rs armazena na memória
							if (registradorValido(ir.r1) && enderecoValido(ir.p) && testOverflow(reg[ir.r1])) {
								m[ir.p].opc = Opcode.DATA;
								m[ir.p].p = reg[ir.r1];
								pc++;
								break;
							} else
								break;

						case STX: // [Rd] ←Rs armazenamento indireto na memória
							if (registradorValido(ir.r1) && registradorValido(ir.r2) && enderecoValido(reg[ir.r1])
									&& testOverflow(reg[ir.r2])) {
								m[reg[ir.r1]].opc = Opcode.DATA;
								m[reg[ir.r1]].p = reg[ir.r2];
								pc++;
								break;
							} else
								break;

						case MOVE: // RD <- RS
							if (registradorValido(ir.r1) && registradorValido(ir.r2)) {
								reg[ir.r1] = reg[ir.r2];
								pc++;
								break;
							} else
								break;

							// Instrucoes Aritmeticas
						case ADD: // Rd ← Rd + Rs
							if (registradorValido(ir.r1) && registradorValido(ir.r2) &&
									testOverflow(reg[ir.r1]) && testOverflow(reg[ir.r2]) &&
									testOverflow(reg[ir.r1] + reg[ir.r2])) {
								reg[ir.r1] = reg[ir.r1] + reg[ir.r2];
								// testOverflow(reg[ir.r1]);
								pc++;
								break;
							} else
								pc++;
							break;

						case ADDI: // Rd ← Rd + k adição imediata
							if (registradorValido(ir.r1) && testOverflow(reg[ir.r1]) && testOverflow(reg[ir.p]) &&
									testOverflow(reg[ir.r1] + ir.p)) {
								reg[ir.r1] = reg[ir.r1] + ir.p;
								pc++;
								break;
							} else
								pc++;
							break;

						case SUB: // Rd ← Rd - Rs
							if (registradorValido(ir.r1) && registradorValido(ir.r2)
									&& testOverflow(reg[ir.r1]) && testOverflow(reg[ir.r2])
									&& testOverflow(reg[ir.r1] - reg[ir.r2])) {
								reg[ir.r1] = reg[ir.r1] - reg[ir.r2];
								pc++;
								break;
							} else
								pc++;
							break;

						case SUBI: // RD <- RD - k // NOVA
							if (registradorValido(ir.r1) && enderecoValido(ir.p) && testOverflow(reg[ir.r1])
									&& testOverflow(reg[ir.p]) && testOverflow(reg[ir.r1] - ir.p)) {
								reg[ir.r1] = reg[ir.r1] - ir.p;
								pc++;
								break;
							} else
								pc++;
							break;

						case MULT: // Rd <- Rd * Rs
							if (registradorValido(ir.r1) && registradorValido(ir.r2) && testOverflow(reg[ir.r1])
									&& testOverflow(reg[ir.r2]) && testOverflow(reg[ir.r1] * reg[ir.r2])) {
								reg[ir.r1] = reg[ir.r1] * reg[ir.r2];
								pc++;
								break;
							} else
								pc++;
							break;

						// Instrucoes JUMP
						case JMP: // PC <- k desvio incondicional
							if (enderecoValido(ir.p)) {
								pc = ir.p;
								break;
							} else
								break;

						case JMPIG: // If Rc > 0 Then PC ← Rs Else PC ← PC +1 desvio condicinal
							if (registradorValido(ir.r2) && registradorValido(ir.r1) && enderecoValido(reg[ir.r1])) {
								if (reg[ir.r2] > 0) {
									pc = reg[ir.r1];
								} else {
									pc++;
								}
								break;
							} else {
								break;
							}

						case JMPIGK: // If RC > 0 then PC <- k else PC++
							if (registradorValido(ir.r2) && enderecoValido(ir.p)) {
								if (reg[ir.r2] > 0) {
									pc = ir.p;
								} else {
									pc++;
								}
								break;
							} else {
								break;
							}

						case JMPILK: // If RC < 0 then PC <- k else PC++
							if (registradorValido(ir.r2) && enderecoValido(ir.p)) {
								if (reg[ir.r2] < 0) {
									pc = ir.p;
								} else {
									pc++;
								}
								break;
							} else {
								break;
							}

						case JMPIEK: // If RC = 0 then PC <- k else PC++
							if (registradorValido(ir.r2) && enderecoValido(ir.p)) {
								if (reg[ir.r2] == 0) {
									pc = ir.p;
								} else {
									pc++;
								}
								break;
							} else {
								break;
							}

						case JMPIL: // if Rc < 0 then PC <- Rs Else PC <- PC +1
							if (registradorValido(ir.r2) && registradorValido(ir.r1) &&
									enderecoValido(reg[ir.r1])) {
								if (reg[ir.r2] < 0) {
									pc = reg[ir.r1];
								} else {
									pc++;
								}
								break;
							} else {
								break;
							}

						case JMPIE: // If Rc = 0 Then PC <- Rs Else PC <- PC +1
							if (registradorValido(ir.r2) && registradorValido(ir.r1) && enderecoValido(reg[ir.r1])) {
								if (reg[ir.r2] == 0) {
									pc = reg[ir.r1];
								} else {
									pc++;
								}
								break;
							} else {
								break;
							}

						case JMPIM: // PC <- [A]
							if (enderecoValido(ir.p) && enderecoValido(m[ir.p].p)) {
								pc = m[ir.p].p;
								break;
							} else {
								break;
							}
						case JMPIGM: // If RC > 0 then PC <- [A] else PC++
							if (registradorValido(ir.r2) && enderecoValido(ir.p) &&
									enderecoValido(m[ir.p].p)) {
								if (reg[ir.r2] > 0) {
									pc = m[ir.p].p;
								} else {
									pc++;
								}
								break;
							} else {
								break;
							}

						case JMPILM: // If RC < 0 then PC <- k else PC++
							if (registradorValido(ir.r2) && enderecoValido(ir.p) && enderecoValido(m[ir.p].p)) {
								if (reg[ir.r2] < 0) {
									pc = m[ir.p].p;
								} else {
									pc++;
								}
								break;
							} else {
								break;
							}

						case JMPIEM: // If RC = 0 then PC <- k else PC++
							if (registradorValido(ir.r2) && enderecoValido(ir.p) && enderecoValido(m[ir.p].p)) {
								if (reg[ir.r2] == 0) {
									pc = m[ir.p].p;
								} else {
									pc++;
								}
								break;
							} else {
								break;
							}

						case JMPIGT: // If RS>RC then PC <- k else PC++
							if (registradorValido(ir.r2) && registradorValido(ir.r1) && enderecoValido(ir.p)) {
								if (reg[ir.r1] > reg[ir.r2]) {
									pc = ir.p;
								} else {
									pc++;
								}
								break;
							} else {
								break;
							}

							// outras
						case STOP: // por enquanto, para execucao
							irpt = Interrupts.intSTOP;
							break;

						case DATA:
							pc++;
							break;

						// Chamada de sistema
						case TRAP:
							sysCall.handle(); // <<<<< aqui desvia para rotina de chamada de sistema, no momento so
												// temos IO
							pc++;
							break;

						// Inexistente
						default:
							irpt = Interrupts.intInstrucaoInvalida;
							break;
					}
				}
				// --------------------------------------------------------------------------------------------------
				// VERIFICA INTERRUPÇÃO !!! - TERCEIRA FASE DO CICLO DE INSTRUÇÕES
				if (!(irpt == Interrupts.noInterrupt)) { // existe interrupção
					ih.handle(irpt, pc); // desvia para rotina de tratamento
					break; // break sai do loop da cpu
				}
			} // FIM DO CICLO DE UMA INSTRUÇÃO
		}
	}
	// ------------------ C P U - fim
	// ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// ------------------- V M - constituida de CPU e MEMORIA
	// -----------------------------------------------
	// -------------------------- atributos e construcao da VM
	// -----------------------------------------------
	public class VM {
		public int tamMem; 
		public int tamParticao;   
        public Word[] m;  
		public Memory mem;   
        public CPU cpu;    

        public VM(InterruptHandling ih, SysCallHandling sysCall, int tamMem, int tamParticao) {   
		 // vm deve ser configurada com endereço de tratamento de interrupcoes e de chamadas de sistema
	     // cria memória
		     this.tamMem = tamMem;
			 this.tamParticao = tamParticao;
  		 	 mem = new Memory(tamMem);
			 m = mem.m;

	  	 // cria cpu
			 cpu = new CPU(mem, tamParticao,ih,sysCall, true);                   // true liga debug
	    }
		
		public int getTamMem() {
            return tamMem;
        }

		public int getTamParticao() {
			return tamParticao;
		}

	}
	// ------------------- V M - fim
	// ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// --------------------H A R D W A R E - fim
	// -------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// ------------------- S O F T W A R E - inicio
	// ----------------------------------------------------------

	// ------------------- I N T E R R U P C O E S - rotinas de tratamento
	// ----------------------------------
	public class InterruptHandling {
		public void handle(Interrupts irpt, int pc) { // apenas avisa - todas interrupcoes neste momento finalizam o
														// programa
			System.out.println("                                               Interrupcao " + irpt + "   pc: " + pc);

			switch (irpt) {
				case intEnderecoInvalido:
					System.out.println("Motivo: Endereço de memória inválido");
					finalizaPrograma();
					break;
				case intInstrucaoInvalida:
					System.out.println("Motivo: Instrução inválida");
					finalizaPrograma();
					break;
				case intRegistradorInvalido:
					System.out.println("Motivo: Registrador inválido");
					finalizaPrograma();
					break;
				case intOverflow:
					System.out.println("Motivo: Overflow");
					finalizaPrograma();
					break;
				case chamadaTrap:
					System.out.println("Motivo: Chamada de sistema");
					terminal();
					break;
			}
		}

		private void terminal() {
			Locale.setDefault(Locale.US);
			Scanner sc = new Scanner(System.in);

			if (vm.cpu.reg[8] == 1) {// leitura de um inteiro
				int pos = vm.cpu.reg[9];
				System.out.println(
						"                                               Lendo da função Trap para pos de memória: "
								+ pos);
				int var = sc.nextInt();
				vm.m[pos].p = var;
			}
			if (vm.cpu.reg[8] == 2) { // escrita de um inteiro
				int pos = vm.cpu.reg[9];
				int var = vm.m[pos].p;
				vm.m[pos].p = var;
				System.out
						.println("                                               Imprimindo da função Trap: resposta: "
								+ var + " e pos de memória: " + pos);
			}

		}

		private void finalizaPrograma() {
			System.out.println("Fim do programa por interrupção.");
			System.exit(0);
		}

	}

	// ------------------- C H A M A D A S D E S I S T E M A - rotinas de tratamento
	// ----------------------
	public class SysCallHandling {
		private VM vm;

		public void setVM(VM _vm) {
			vm = _vm;
		}

		public void handle() { // apenas avisa - todas interrupcoes neste momento finalizam o programa
			System.out.println("                                               Chamada de Sistema com op  /  par:  "
					+ vm.cpu.reg[8] + " / " + vm.cpu.reg[9]);

			ih.handle(Interrupts.chamadaTrap, vm.cpu.pc);
		}
	}

	// ------------------ U T I L I T A R I O S D O S I S T E M A
	// -----------------------------------------
	// ------------------ load é invocado a partir de requisição do usuário


	private void loadProgram(Word[] p, Word[] m) {
		for (int i = 0; i < p.length; i++) {
			m[i].opc = p[i].opc;
			m[i].r1 = p[i].r1;
			m[i].r2 = p[i].r2;
			m[i].p = p[i].p;
		}
	}

	private void loadProgram(Word[] p) {
		loadProgram(p, vm.m);
	}

	private void loadAndExec(Word[] p) {
		loadProgram(p); // carga do programa na memoria
		System.out.println("---------------------------------- programa carregado na memoria");
		vm.mem.dump(0, p.length); // dump da memoria nestas posicoes
		vm.cpu.setContext(0, vm.tamMem - 1, 0); // seta estado da cpu ]
		System.out.println("---------------------------------- inicia execucao ");
		vm.cpu.run(); // cpu roda programa ate parar
		System.out.println("---------------------------------- memoria após execucao ");
		vm.mem.dump(0, p.length); // dump da memoria com resultado
	}

	// Aqui estou criando as funções para cada opção do Menu

	//case 1
	private void loadAndExecGM_GP(Word[] p){ 
		//aciona o GP para criar processos
		//gm.imprimeParticao(vm.m, 0, 128);
		gp.criaProcesso(p);
		//aciona o GM para imprimir partição
		//gm.imprimeParticao(vm.m, 0, 128);
				
	}

	//case 2
	private void listaProcessosPCB(){
		gp.listaProcessosPCB();
	}

	//case 3
	private void desaloca(int id){
		gp.desalocaProcesso(id);
	}

	//case 4
	private void dumpM(int inicio, int fim){
		System.out.println("Imprimindo memoria: de " + inicio + " até " + fim);
		gm.imprimeParticao(vm.m, inicio, fim);
	}

	//case 5
	private void executa(int idProcesso){
		//executa o processo
		PCB pcbProcesso = prontos.get(idProcesso);
		if (pcbProcesso == null){
			System.out.println("Processo não encontrado");
		}else{
		System.out.println("---------------------------------- inicia execucao ");
		vm.cpu.run(); // cpu roda programa ate parar
		}
	}

	//case 6

	//case 7

	//case 8
	public void listaProcessosProntos(){
		gp.listaProcessos();
	}

	// ------------------- GM GP PCB - inicio


	public class GM{

		private Word[] m;
		private Memory mem;
		private int tamMem;
		private int tamPart;
		private int partAlocada;
		private int numPart;
		private LinkedList<PCB> prontos;

		public boolean [] particoesLivres;

		public GM(int tamMem, int tamPart){
			this.tamMem = tamMem;
			this.tamPart = tamPart;
			numPart= tamMem/tamPart;
			this.partAlocada = -1;
			this.particoesLivres = new boolean[numPart];
			for(int i = 0; i < numPart; i++){
				particoesLivres[i] = true;
			}

			//simulando partição ocupada
            //particoesLivres[0]=false;
		}

		
		private int alocaParticao(Word[] programa){
			if (programa.length > tamPart) {
				System.out.println("Programa muito grande para o tamanho de partição");
				return -1;
			}

			for (int i = 0; i < numPart; i++) {//percorre as partiçoes, a primeira que estiver livre retorna para o GP
				if (particoesLivres[i]) {
					particoesLivres[i] = false;
					partAlocada = i * tamPart;
					System.out.println("Alocar na partição: " + i + " que inicia na posição: " + partAlocada);
					return i;
				}
			}
			System.out.println("Não há partições livres");
			return 0; // se 0 significa que não há partições livres


		}

		private void desalocaParticao(int particao){
			//particao = particao/tamPart;
				for(int i = 0; i < numPart; i++){
				if(particao == i){
					particoesLivres[i] = true;
					System.out.println("Partição desalocada: " + particao);
				}
			}
		}

		public void dump(Word w) {
            System.out.print("[ ");
            System.out.print(w.opc);
            System.out.print(", ");
            System.out.print(w.r1);
            System.out.print(", ");
            System.out.print(w.r2);
            System.out.print(", ");
            System.out.print(w.p);
            System.out.println("  ] ");
        }


		public void imprimeParticao(Word [] m, int inicio, int fim){
			//System.out.println("Partição: " + inicio + " até " + fim);
			//int inicio = traduzEnderecoFisico(prontos.get().getParticao());

			for (int i=inicio; i<=fim; i++){
				System.out.print("Posição da Memória " +i);
                System.out.print(":  ");
				dump(m[i]);

			}
		}

		private int traduzEnderecoFisico(int enderecoPrograma, int particaoPrograma){// função que traduz para o enederço físico da memória
			if (enderecoPrograma < 0 || enderecoPrograma > tamPart) {
				System.out.println("Endereço inválido");
				return -1;
			}
			int enderecoFisico = (particaoPrograma* tamPart) - 1 + enderecoPrograma;
			return enderecoFisico;
		}

		private int traduzEnderecoFisico( int particaoPrograma){// função que traduz para o enederço físico da memória
	 		int enderecoFisico = (particaoPrograma* tamPart);
			return enderecoFisico;
		}
		
	}

	public class GP{
		private GM gm;
		private Word[] mem;
		private int idProcesso;
		private LinkedList<PCB> prontos;
		private int idProcessoRodando;
		

		
		public GP(){
			this.idProcesso = 0;
			this.idProcessoRodando = 0;
		}

		public void carregaGP(GM gm, Word[] mem, LinkedList<PCB> prontos) {
            this.gm = gm;
            this.mem = mem;
            this.prontos = prontos;
        }
		
		private boolean criaProcesso(Word[] programa){
			int particao = gm.alocaParticao(programa);
			if (particao == -1) {
				System.out.println("Não foi possível ciar processo em memória");
				return false;
			}
			//carga do processo na partição retornada por GM
			for (int i = 0; i < programa.length; i++) {
				mem[gm.traduzEnderecoFisico(particao)+i].opc = programa[i].opc;
				mem[gm.traduzEnderecoFisico(particao)+i].r1 = programa[i].r1;
				mem[gm.traduzEnderecoFisico(particao)+i].r2 = programa[i].r2;
				mem[gm.traduzEnderecoFisico(particao)+i].p = programa[i].p;
				
			}
			/*
			for (int i = 0; i < programa.length; i++) {
				mem[particao + i].opc = programa[i].opc;
				mem[particao + i].r1 = programa[i].r1;
				mem[particao + i].r2 = programa[i].r2;
				mem[particao + i].p = programa[i].p;				
			}
			 */

			PCB pcbProcesso = new PCB();
			pcbProcesso.setPCB(particao,idProcesso,0, programa.length);
			prontos.add(pcbProcesso);
			System.out.println("Processo criado com id: " + idProcesso + " na posição: " + particao);
			idProcesso++;
			
			return true;

		}

		private void desalocaProcesso (int idProcesso){
			PCB pcbProcesso = prontos.get(idProcesso);

			int inicioDesaloca = gm.traduzEnderecoFisico(pcbProcesso.getParticao());
			int fimDesaloca = inicioDesaloca + pcbProcesso.getTamanhoProcesso();
			//remoção do processo na partição 
			for (int i = inicioDesaloca; i <= fimDesaloca; i++) {
				mem[i].opc =  Opcode.___;
				mem[i].r1 = -1;
				mem[i].r2 = -1;
				mem[i].p = -1;				
			}

			gm.desalocaParticao(pcbProcesso.getParticao());
			System.out.println("Processo desalocado de id " + idProcesso + " desalocado com sucesso.");
			prontos.remove(idProcesso);
		}

		public void imprimeProcessoPorID(int id){
			PCB pcbProcesso = prontos.get(id);
			int particao = pcbProcesso.getParticao();
			int inicio = gm.traduzEnderecoFisico(particao);
			int fim = inicio + pcbProcesso.getTamanhoProcesso();
			for (int i=inicio; i<=fim; i++){
				gm.dump(mem[i]);
			}
		}

		public void listaProcessos(){
			System.out.println("Lista de processos criados:");

			for (int i=0; i<prontos.size(); i++){
				PCB pcbProcesso = prontos.get(i);

				System.out.println("Processo: " + pcbProcesso.getIdProcesso() + " na partição: " + (prontos.get(i).getParticao()) + " posição física: " + gm.traduzEnderecoFisico(prontos.get(i).getParticao()));

			}
		}

		public void listaProcessosPCB(){
			System.out.println("Lista de PCB de cada processos: ");

			for (int i=0; i<prontos.size(); i++){
				PCB pcbProcesso = prontos.get(i);

				System.out.println("Processo: " + pcbProcesso.getIdProcesso() + " na partição: " + (prontos.get(i).getParticao()) + " tamanho do processo: " + pcbProcesso.getTamanhoProcesso() + " posição física: " + gm.traduzEnderecoFisico(prontos.get(i).getParticao()));

			}
		}

	}

	public class PCB{
		private int idProcesso;
		private int particao;
		private int pc;
		private int tamanhoProcesso;

		public void setPCB(int particao, int idProcesso, int pc, int tamanhoProcesso) {
			this.particao = particao;
			this.idProcesso = idProcesso;
			this.pc = pc;
			this.tamanhoProcesso = tamanhoProcesso;
		}

		public int getParticao(){
			return particao;
		}

		public int getIdProcesso(){
			return idProcesso;
		}	

		public int getPc(){
			return pc;
		}

		public int getTamanhoProcesso(){
			return tamanhoProcesso;
		}
	}


	// ------------------ GM GP PCB - fim

	

	// -------------------------------------------------------------------------------------------------------
	// ------------------- S I S T E M A
	// --------------------------------------------------------------------

	public VM vm;
	public InterruptHandling ih;
	public SysCallHandling sysCall;
	public static Programas progs;
	public GM gm;
	public GP gp;
	private LinkedList<PCB> prontos;

	public SistemaT1_v1(int tamMem, int tamPart) { // a VM com tratamento de interrupções
		ih = new InterruptHandling();
		sysCall = new SysCallHandling();
		vm = new VM(ih, sysCall, tamMem, tamPart);
		sysCall.setVM(vm);
		progs = new Programas();
		gm = new GM(vm.tamMem, tamPart);
		gp = new GP();
		prontos = new LinkedList();
		
		gp.carregaGP(gm, vm.m, prontos);
		//gm.imprimeParticao(0);
		
	}
	



	// ------------------- S I S T E M A - fim
	// --------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// ------------------- instancia e testa sistema
	public static void main(String args[]) {

        int tamMemoria = 1024;
        int tamParticao = 64;

		//--------- Menu ----------------------------------------------------------------
		Scanner sc = new Scanner(System.in);

		SistemaT1_v1 s = new SistemaT1_v1(tamMemoria, tamParticao);


		while (true){
			System.out.println("Escolha uma opção:");
			System.out.println("1 - Cria programa - cria um processo com memória alocada, PCB, etc."); //ok
			System.out.println("2 - Dump - lista o conteúdo do PCB."); //ok
			System.out.println("3 - Desaloca - retira o processo id do sistema."); //ok
			System.out.println("4 - DumpM - lista a memória entre posições início e fim."); //ok
			System.out.println("5 - Executa - executa o processo com id fornecido.");
			System.out.println("6 - TraceOn - liga modo de execução em que CPU print cada instrução executada.");
			System.out.println("7 - TraceOff - desliga o modo acima TraceOn.");
			System.out.println("8 - ListaProcessos - lista os processos prontos para rodar.");//ok
			System.out.println("0 - Sair - encerra o programa.");//ok

			int option;
			option = sc.nextInt();

			switch (option){
				case 1:
					System.out.println("Escolha um programa da lista:");
					System.out.println("1 - Fibonacci");
					System.out.println("2 - ProgMinimo");
					System.out.println("3 - Fatorial");
					System.out.println("4 - fatorialTRAP");
					System.out.println("5 - fibonacciTRAP");
					System.out.println("6 - bubble sort");
					System.out.println("7 - testeLeitura");
					System.out.println("8 - testeEscrita");
					System.out.println("0 - Sair");
					int option2;
					option2 = sc.nextInt();

					switch (option2){
						case 1:
							s.loadAndExecGM_GP(progs.fibonacci10);
							break;
						case 2:
							s.loadAndExecGM_GP(progs.progMinimo);
							break;
						case 3:
							s.loadAndExecGM_GP(progs.fatorial);
							break;
						case 4:
							s.loadAndExecGM_GP(progs.fatorialTRAP);
							break;
						case 5:	
							s.loadAndExecGM_GP(progs.fibonacciTRAP);
							break;
						case 6:
							s.loadAndExecGM_GP(progs.PC);
							break;
						case 7:
							s.loadAndExecGM_GP(progs.testeLeitura);
							break;
						case 8:
							s.loadAndExecGM_GP(progs.testeEscrita);
							break;
						case 0:
							break;							
					}					
			
			case 2:
				s.listaProcessosPCB();
				break;

			case 3:
				System.out.println("Digite o id do processo a ser desalocado:");
				int id;
				id = sc.nextInt();
				s.desaloca(id);
				break;

			case 4: 
				System.out.println("Digite a posição inicial:");
				int ini;
				ini = sc.nextInt();
				System.out.println("Digite a posição final:");
				int fim;
				fim = sc.nextInt();
				s.dumpM(ini, fim);
				break;

			case 5: 
				System.out.println("Digite o id do processo a ser executado:");
				int id2;
				id2 = sc.nextInt();
				s.executa(id2);
				break;

			case 6:
				break;
			case 7:
				break;

			case 8:
				s.listaProcessosProntos();
				break;	
			case 0:
				System.exit(0); 
				
			}
		}	

		//--------- Menu - fim ------------------------------------------------------------

		//SistemaT1_v1 s = new SistemaT1_v1(tamMemoria, tamParticao);
		//System.out.println("Sistema iniciado");
		//System.out.println("Memória: " + tamMemoria);
		//System.out.println("Tamanho partição: " + tamParticao );
		//s.loadAndExecGM_GP(progs.fibonacci10);
		//s.loadAndExecGM_GP(progs.fatorial);
		// s.loadAndExec(progs.progMinimo);
		// s.loadAndExec(progs.fatorial);
		// s.loadAndExec(progs.fatorialTRAP); // saida
		// s.loadAndExec(progs.fibonacciTRAP); // entrada
		// s.loadAndExec(progs.PC); // bubble sort

		// Teste interrupções
		// s.loadAndExec(progs.testeLeitura);//teste leitura
		// s.loadAndExec(progs.testeEscrita);//teste escrita
		// s.loadAndExec(progs.fatorialTeste); // testando registrador inválido
		// s.loadAndExec(progs.fatorialTesteEndereco); // testando endereço inválido
		// s.loadAndExec(progs.TestandoOverflow); // testando overflow na soma
		//s.loadAndExec(progs.TestandoOverflowParam); // testando overflow no parametro recebido

	}

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// --------------- P R O G R A M A S - não fazem parte do sistema
	// esta classe representa programas armazenados (como se estivessem em disco)
	// que podem ser carregados para a memória (load faz isto)

	public class Programas {
		public Word[] fatorial = new Word[] {
				// este fatorial so aceita valores positivos. nao pode ser zero
				// linha coment
				new Word(Opcode.LDI, 0, -1, 4), // 0 r0 é valor a calcular fatorial
				new Word(Opcode.LDI, 1, -1, 1), // 1 r1 é 1 para multiplicar (por r0)
				new Word(Opcode.LDI, 6, -1, 1), // 2 r6 é 1 para ser o decremento
				new Word(Opcode.LDI, 7, -1, 8), // 3 r7 tem posicao de stop do programa = 8
				new Word(Opcode.JMPIE, 7, 0, 0), // 4 se r0=0 pula para r7(=8)
				new Word(Opcode.MULT, 1, 0, -1), // 5 r1 = r1 * r0
				new Word(Opcode.SUB, 0, 6, -1), // 6 decrementa r0 1
				new Word(Opcode.JMP, -1, -1, 4), // 7 vai p posicao 4
				new Word(Opcode.STD, 1, -1, 10), // 8 coloca valor de r1 na posição 10
				new Word(Opcode.STOP, -1, -1, -1), // 9 stop
				new Word(Opcode.DATA, -1, -1, -1) }; // 10 ao final o valor do fatorial estará na posição 10 da memória

		public Word[] progMinimo = new Word[] {
				new Word(Opcode.LDI, 0, -1, 999),
				new Word(Opcode.STD, 0, -1, 10),
				new Word(Opcode.STD, 0, -1, 11),
				new Word(Opcode.STD, 0, -1, 12),
				new Word(Opcode.STD, 0, -1, 13),
				new Word(Opcode.STD, 0, -1, 14),
				new Word(Opcode.STOP, -1, -1, -1) };

		public Word[] fibonacci10 = new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.STD, 1, -1, 20),
				new Word(Opcode.LDI, 2, -1, 1),
				new Word(Opcode.STD, 2, -1, 21),
				new Word(Opcode.LDI, 0, -1, 22),
				new Word(Opcode.LDI, 6, -1, 6),
				new Word(Opcode.LDI, 7, -1, 31),
				new Word(Opcode.LDI, 3, -1, 0),
				new Word(Opcode.ADD, 3, 1, -1),
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.ADD, 1, 2, -1),
				new Word(Opcode.ADD, 2, 3, -1),
				new Word(Opcode.STX, 0, 2, -1),
				new Word(Opcode.ADDI, 0, -1, 1),
				new Word(Opcode.SUB, 7, 0, -1),
				new Word(Opcode.JMPIG, 6, 7, -1),
				new Word(Opcode.STOP, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1), // POS 20
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1) }; // ate aqui - serie de fibonacci ficara armazenada

		public Word[] fatorialTRAP = new Word[] {
				new Word(Opcode.LDI, 0, -1, 7), // numero para colocar na memoria
				new Word(Opcode.STD, 0, -1, 50),
				new Word(Opcode.LDD, 0, -1, 50),
				new Word(Opcode.LDI, 1, -1, -1),
				new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
				new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
				new Word(Opcode.LDI, 1, -1, 1),
				new Word(Opcode.LDI, 6, -1, 1),
				new Word(Opcode.LDI, 7, -1, 13),
				new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula pra STD (Stop-1)
				new Word(Opcode.MULT, 1, 0, -1),
				new Word(Opcode.SUB, 0, 6, -1),
				new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
				new Word(Opcode.STD, 1, -1, 18),
				new Word(Opcode.LDI, 8, -1, 2), // escrita
				new Word(Opcode.LDI, 9, -1, 18), // endereco com valor a escrever
				new Word(Opcode.TRAP, -1, -1, -1),
				new Word(Opcode.STOP, -1, -1, -1), // POS 17
				new Word(Opcode.DATA, -1, -1, -1) };// POS 18

		public Word[] fibonacciTRAP = new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
				new Word(Opcode.LDI, 8, -1, 1), // leitura
				new Word(Opcode.LDI, 9, -1, 100), // endereco a guardar
				new Word(Opcode.TRAP, -1, -1, -1),
				new Word(Opcode.LDD, 7, -1, 100), // numero do tamanho do fib
				new Word(Opcode.LDI, 3, -1, 0),
				new Word(Opcode.ADD, 3, 7, -1),
				new Word(Opcode.LDI, 4, -1, 36), // posicao para qual ira pular (stop) *
				new Word(Opcode.LDI, 1, -1, -1), // caso negativo
				new Word(Opcode.STD, 1, -1, 41),
				new Word(Opcode.JMPIL, 4, 7, -1), // pula pra stop caso negativo *
				new Word(Opcode.JMPIE, 4, 7, -1), // pula pra stop caso 0
				new Word(Opcode.ADDI, 7, -1, 41), // fibonacci + posição do stop
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.STD, 1, -1, 41), // 25 posicao de memoria onde inicia a serie de fibonacci gerada
				new Word(Opcode.SUBI, 3, -1, 1), // se 1 pula pro stop
				new Word(Opcode.JMPIE, 4, 3, -1),
				new Word(Opcode.ADDI, 3, -1, 1),
				new Word(Opcode.LDI, 2, -1, 1),
				new Word(Opcode.STD, 2, -1, 42),
				new Word(Opcode.SUBI, 3, -1, 2), // se 2 pula pro stop
				new Word(Opcode.JMPIE, 4, 3, -1),
				new Word(Opcode.LDI, 0, -1, 43),
				new Word(Opcode.LDI, 6, -1, 25), // salva posição de retorno do loop
				new Word(Opcode.LDI, 5, -1, 0), // salva tamanho
				new Word(Opcode.ADD, 5, 7, -1),
				new Word(Opcode.LDI, 7, -1, 0), // zera (inicio do loop)
				new Word(Opcode.ADD, 7, 5, -1), // recarrega tamanho
				new Word(Opcode.LDI, 3, -1, 0),
				new Word(Opcode.ADD, 3, 1, -1),
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.ADD, 1, 2, -1),
				new Word(Opcode.ADD, 2, 3, -1),
				new Word(Opcode.STX, 0, 2, -1),
				new Word(Opcode.ADDI, 0, -1, 1),
				new Word(Opcode.SUB, 7, 0, -1),
				new Word(Opcode.JMPIG, 6, 7, -1), // volta para o inicio do loop
				new Word(Opcode.STOP, -1, -1, -1), // POS 36
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1), // POS 41
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1)
		};

		public Word[] PB = new Word[] {
				// dado um inteiro em alguma posição de memória,
				// se for negativo armazena -1 na saída; se for positivo responde o fatorial do
				// número na saída
				new Word(Opcode.LDI, 0, -1, 7), // numero para colocar na memoria
				new Word(Opcode.STD, 0, -1, 50),
				new Word(Opcode.LDD, 0, -1, 50),
				new Word(Opcode.LDI, 1, -1, -1),
				new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
				new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
				new Word(Opcode.LDI, 1, -1, 1),
				new Word(Opcode.LDI, 6, -1, 1),
				new Word(Opcode.LDI, 7, -1, 13),
				new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula pra STD (Stop-1)
				new Word(Opcode.MULT, 1, 0, -1),
				new Word(Opcode.SUB, 0, 6, -1),
				new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
				new Word(Opcode.STD, 1, -1, 15),
				new Word(Opcode.STOP, -1, -1, -1), // POS 14
				new Word(Opcode.DATA, -1, -1, -1) }; // POS 15

		public Word[] PC = new Word[] {
				// Para um N definido (10 por exemplo)
				// o programa ordena um vetor de N números em alguma posição de memória;
				// ordena usando bubble sort
				// loop ate que não swap nada
				// passando pelos N valores
				// faz swap de vizinhos se da esquerda maior que da direita
				new Word(Opcode.LDI, 7, -1, 5), // TAMANHO DO BUBBLE SORT (N)
				new Word(Opcode.LDI, 6, -1, 5), // aux N
				new Word(Opcode.LDI, 5, -1, 46), // LOCAL DA MEMORIA
				new Word(Opcode.LDI, 4, -1, 47), // aux local memoria
				new Word(Opcode.LDI, 0, -1, 4), // colocando valores na memoria
				new Word(Opcode.STD, 0, -1, 46),
				new Word(Opcode.LDI, 0, -1, 3),
				new Word(Opcode.STD, 0, -1, 47),
				new Word(Opcode.LDI, 0, -1, 5),
				new Word(Opcode.STD, 0, -1, 48),
				new Word(Opcode.LDI, 0, -1, 1),
				new Word(Opcode.STD, 0, -1, 49),
				new Word(Opcode.LDI, 0, -1, 2),
				new Word(Opcode.STD, 0, -1, 50), // colocando valores na memoria até aqui - POS 13
				new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 1
				new Word(Opcode.STD, 3, -1, 99),
				new Word(Opcode.LDI, 3, -1, 22), // Posicao para pulo CHAVE 2
				new Word(Opcode.STD, 3, -1, 98),
				new Word(Opcode.LDI, 3, -1, 38), // Posicao para pulo CHAVE 3
				new Word(Opcode.STD, 3, -1, 97),
				new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 4 (não usada)
				new Word(Opcode.STD, 3, -1, 96),
				new Word(Opcode.LDI, 6, -1, 0), // r6 = r7 - 1 POS 22
				new Word(Opcode.ADD, 6, 7, -1),
				new Word(Opcode.SUBI, 6, -1, 1), // ate aqui
				new Word(Opcode.JMPIEM, -1, 6, 97), // CHAVE 3 para pular quando r7 for 1 e r6 0 para interomper o loop
													// de vez do programa
				new Word(Opcode.LDX, 0, 5, -1), // r0 e r1 pegando valores das posições da memoria POS 26
				new Word(Opcode.LDX, 1, 4, -1),
				new Word(Opcode.LDI, 2, -1, 0),
				new Word(Opcode.ADD, 2, 0, -1),
				new Word(Opcode.SUB, 2, 1, -1),
				new Word(Opcode.ADDI, 4, -1, 1),
				new Word(Opcode.SUBI, 6, -1, 1),
				new Word(Opcode.JMPILM, -1, 2, 99), // LOOP chave 1 caso neg procura prox
				new Word(Opcode.STX, 5, 1, -1),
				new Word(Opcode.SUBI, 4, -1, 1),
				new Word(Opcode.STX, 4, 0, -1),
				new Word(Opcode.ADDI, 4, -1, 1),
				new Word(Opcode.JMPIGM, -1, 6, 99), // LOOP chave 1 POS 38
				new Word(Opcode.ADDI, 5, -1, 1),
				new Word(Opcode.SUBI, 7, -1, 1),
				new Word(Opcode.LDI, 4, -1, 0), // r4 = r5 + 1 POS 41
				new Word(Opcode.ADD, 4, 5, -1),
				new Word(Opcode.ADDI, 4, -1, 1), // ate aqui
				new Word(Opcode.JMPIGM, -1, 7, 98), // LOOP chave 2
				new Word(Opcode.STOP, -1, -1, -1), // POS 45
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1) };

		public Word[] testeLeitura = new Word[] {
				new Word(Opcode.LDI, 8, -1, 1),
				new Word(Opcode.LDI, 9, -1, 4),
				new Word(Opcode.TRAP, -1, -1, -1),
				new Word(Opcode.STOP, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1) };

		public Word[] testeEscrita = new Word[] {
				new Word(Opcode.LDI, 0, -1, 999),
				new Word(Opcode.STD, 0, -1, 10),
				new Word(Opcode.LDI, 8, -1, 2),
				new Word(Opcode.LDI, 9, -1, 10),
				new Word(Opcode.TRAP, -1, -1, -1),
				new Word(Opcode.STOP, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1) };

		// Teste registrador inválido
		public Word[] fatorialTeste = new Word[] {
				// este fatorial so aceita valores positivos. nao pode ser zero
				// linha coment
				new Word(Opcode.LDI, 0, -1, 4), // 0 r0 é valor a calcular fatorial
				new Word(Opcode.LDI, 1, -1, 1), // 1 r1 é 1 para multiplicar (por r0)
				new Word(Opcode.LDI, 12, -1, 1), // 2 r6 é 1 para ser o decremento
				new Word(Opcode.LDI, 7, -1, 8), // 3 r7 tem posicao de stop do programa = 8
				new Word(Opcode.JMPIE, 7, 0, 0), // 4 se r0=0 pula para r7(=8)
				new Word(Opcode.MULT, 1, 0, -1), // 5 r1 = r1 * r0
				new Word(Opcode.SUB, 0, 6, -1), // 6 decrementa r0 1
				new Word(Opcode.JMP, -1, -1, 4), // 7 vai p posicao 4
				new Word(Opcode.STD, 1, -1, 10), // 8 coloca valor de r1 na posição 10
				new Word(Opcode.STOP, -1, -1, -1), // 9 stop
				new Word(Opcode.DATA, -1, -1, -1) }; // 10 ao final o valor do fatorial estará na posição 10 da memória

		public Word[] fatorialTesteEndereco = new Word[] {

				// linha coment
				new Word(Opcode.LDI, 0, -1, 4), // 0 r0 é valor a calcular fatorial
				new Word(Opcode.LDI, 1, -1, 1), // 1 r1 é 1 para multiplicar (por r0)
				new Word(Opcode.LDI, 6, -1, 1), // 2 r6 é 1 para ser o decremento
				new Word(Opcode.LDI, 7, -1, 8), // 3 r7 tem posicao de stop do programa = 8
				new Word(Opcode.JMPIE, 7, 0, 0), // 4 se r0=0 pula para r7(=8)
				new Word(Opcode.MULT, 1, 0, -1), // 5 r1 = r1 * r0
				new Word(Opcode.SUB, 0, 6, -1), // 6 decrementa r0 1
				new Word(Opcode.JMP, -1, -1, 4), // 7 vai p posicao 4
				new Word(Opcode.STD, 1, -1, 32768), // 8 coloca valor de r1 na posição 10
				new Word(Opcode.STOP, -1, -1, -1), // 9 stop
				new Word(Opcode.DATA, -1, -1, -1) }; // 10 ao final o valor do fatorial estará na posição 10 da memória

		public Word[] TestandoOverflow = new Word[] {

				// linha coment
				new Word(Opcode.LDI, 0, -1, 4),
				new Word(Opcode.LDI, 1, -1, 32000),
				new Word(Opcode.LDI, 3, -1, 1000),
				new Word(Opcode.ADD, 3, 1, -1),
				new Word(Opcode.STD, 3, -1, 6),
				new Word(Opcode.STOP, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1) };

		public Word[] TestandoOverflowParam = new Word[] {

				// linha coment
				new Word(Opcode.LDI, 0, -1, 4),
				new Word(Opcode.LDI, 1, -1, 32800),
				new Word(Opcode.LDI, 3, -1, 3),
				new Word(Opcode.ADD, 3, 1, -1),
				new Word(Opcode.STD, 3, -1, 6),
				new Word(Opcode.STOP, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1) };

	}
}
