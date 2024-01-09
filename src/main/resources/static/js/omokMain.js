let roomName;
let nickName;
let inBoard = false;
let myTurn = false;
let coord = {};
let isGameEnd = false;

window.addEventListener("message", function (message) {
    console.log(message);
    if (message.data instanceof Object) {
        const receivedMessage = message.data;
        const roomNameEle = document.querySelector('#roomName');
        switch (receivedMessage.type) {
            case 'player_select' :
                myTurn = true;
                break;
            case 'player_change' :
                myTurn = false;
                break;
            case 'placingStone' :
                if(!document.querySelector('.turn').classList.contains(receivedMessage.color)){
                    return;
                }
                const stone = document.createElement('div');
                stone.className = 'omok-stone ' + receivedMessage.color + ' ' + seq++;
                stone.style.top = receivedMessage.row * 40 + 20 + 'px';
                stone.style.left = receivedMessage.col * 40 + 20 + 'px';
                stone.setAttribute("data-row", receivedMessage.row);
                stone.setAttribute("data-col", receivedMessage.col);
                document.querySelector('.omok-board').appendChild(stone);
                if(receivedMessage.color == "black"){
                    document.querySelector('.player.black').classList.remove('turn');
                    document.querySelector('.player.white').classList.add('turn');
                } else {
                    document.querySelector('.player.white').classList.remove('turn');
                    document.querySelector('.player.black').classList.add('turn')
                }
                checkWin();
                break;
            case 'winner' :
                winner = receivedMessage.color;
                alert(winner + ' wins!');
                break;
            case 'create' :
                roomName =receivedMessage.roomName;
                roomNameEle.textContent = 'room Name : ' + roomName;
                nickName = receivedMessage.nickname;
                if(receivedMessage.stoneColor == "black"){
                    document.querySelector('.player1').textContent = nickName;
                } else if(receivedMessage.stoneColor == "white") {
                    document.querySelector('.player2').textContent = nickName;
                }
                userCheck();
                break;
            case 'join' :
                roomName = receivedMessage.roomInfos.roomName;
                roomNameEle.textContent = 'room Name : ' + roomName;
                nickName = receivedMessage.nickname;
                if(document.querySelector('.player1').textContent != 'empty user'){
                    document.querySelector('.player2').textContent = nickName;
                } else {
                    document.querySelector('.player1').textContent = nickName;
                }
                break;
            case 'stoneColorSwitch' :

                break;
            case 'room_enter' :
                GamePanel(receivedMessage.data.name, receivedMessage.data.blackPlayer, receivedMessage.data.whitePlayer);
                break;
        }
    }
});

function ParentToMessage(message){
    window.parent.postMessage(message);
}

let seq = 0;
let winner;
// dot 클릭 이벤트
function handleDotClick(e) {
    if(winner){
        return;
    }
    const row = e.getAttribute("data-row");
    const col = e.getAttribute("data-col");
    const message = {type: 'placingStone', color: seq%2==0 ? 'black' : 'white' , row:row , col:col };
    ParentToMessage(message);
}

//가로,세로,대각선, 역대각선으로 다섯 개 이상의 돌이 연속되어 있는지 확인
function checkWin() {
    const board = document.querySelector('.omok-board');
    const stones = Array.from(board.querySelectorAll('.omok-stone'));

    for (const stone of stones) {
        const stoneColor = stone.classList.contains('black') ? 'black' : 'white';
        const stonePosition = {
            top: parseInt(stone.style.top),
            left: parseInt(stone.style.left)
        };

        if (checkDirection(board, stoneColor, stonePosition, 1, 0) || // 가로
            checkDirection(board, stoneColor, stonePosition, 0, 1) || // 세로
            checkDirection(board, stoneColor, stonePosition, 1, 1) || // 대각선
            checkDirection(board, stoneColor, stonePosition, 1, -1)) {  // 역대각선
            const message = {type: 'winner', color: stoneColor };
            ParentToMessage(message);
            return;
        }
    }
}

// 특정 위치에서 주어진 방향으로 가로,세로,대각선, 역대각선으로 다섯 개 이상의 돌이 연속되어 있는지 확인
function checkDirection(board, color, position, directionX, directionY) {
    const consecutiveStones = [position];
    for (let i = 1; i < 5; i++) {
        const nextPosition = {
            top: position.top + i * directionY * 40,
            left: position.left + i * directionX * 40
        };
        const nextStone = findStoneAtPosition(board, nextPosition);
        if (nextStone && nextStone.classList.contains(color)) {
            consecutiveStones.push(nextPosition);
        } else {
            break;
        }
    }
    for (let i = 1; i < 5; i++) {
        const prevPosition = {
            top: position.top - i * directionY * 40,
            left: position.left - i * directionX * 40
        };
        const prevStone = findStoneAtPosition(board, prevPosition);
        if (prevStone && prevStone.classList.contains(color)) {
            consecutiveStones.push(prevPosition);
        } else {
            break;
        }
    }
    if (consecutiveStones.length >= 5) {
        return true;
    }
    return false;
}

// 주어진 위치에 있는 돌을 찾아 반환
function findStoneAtPosition(board, position) {
    const stones = Array.from(board.querySelectorAll('.omok-stone'));
    return stones.find(stone => {
        const stonePosition = {
            top: parseInt(stone.style.top),
            left: parseInt(stone.style.left)
        };
        return stonePosition.top === position.top && stonePosition.left === position.left;
    });
}

function stoneColorSwitch(e){
    let target;
    if(e.id == "player1"){
        document.querySelector('.player1').textContent = nickName;
        document.querySelector('.player2').textContent = 'empty user';
    } else {
        document.querySelector('.player1').textContent = 'empty user';
        document.querySelector('.player2').textContent = nickName;
    }
    userCheck();
}

function userCheck(){
    if(document.querySelector('.player1').textContent != 'empty user'){
        document.querySelector('#player1').style.display = 'none';
    } else {
        document.querySelector('#player1').style.display = '';
    }
    if(document.querySelector('.player2').textContent != 'empty user'){
        document.querySelector('#player2').style.display = 'none';
    } else {
        document.querySelector('#player2').style.display = '';
    }
}

function fnGetOut(){
    const message = {type: 'getOut'};
    ParentToMessage(message);
};


function GamePanel(roomname, blackPlayer, whitePlayer) {
    const message = [];

    // socket.on("message", (msg) => {
    //     message.push(msg);
    //     render();
    // });

    function Player({ name, onClick }) {
        return (
            "<div class='game-panel__playerinfo'>" +
            (name !== ""
                ? "<p class='game-panel__playername'>" + name + "</p>"
                : "<button class='game-panel__playerselect' onclick='" +
                onClick +
                "'>참가</button>") +
            "</div>"
        );
    }

    function MessageLine(msg) {
        return msg + "<br />";
    }

    function blackPlayerCallback() {
        ParentToMessage({type: 'player_change', data : 'black'});
    }

    function whitePlayerCallback() {
        ParentToMessage({type: 'player_change', data : 'white'});
    }

    function render() {
        const gamePanel = document.querySelector(".game-panel");
        gamePanel.innerHTML = `
      <div class="game-panel__main">
        <h3 class="game-panel__title">${roomname}</h3>
        <div class="game-panel__players">
          <div class="game-panel__player">
            <h4 class="game-panel__playercolor game-panel__playercolor--black">
              Black
            </h4>
            ${Player({ name: blackPlayer, onClick: "blackPlayerCallback()" })}
          </div>
          <div class="game-panel__player">
            <h4 class="game-panel__playercolor game-panel__playercolor--white">White</h4>
            ${Player({ name: whitePlayer, onClick: "whitePlayerCallback()" })}
          </div>
        </div>
        <div class="game-panel__message">
          <p>${message.map(MessageLine).join("")}</p>
        </div>
      </div>
      <div class="game-panel__buttons">
        <button class="game-panel__button" onclick="ParentToMessage({type: 'player_change', data: 'spectator' })">관전하기</button>
        <button class="game-panel__button" onclick="ParentToMessage({type: 'room_leave'})">방 나가기</button>
      </div>
    `;
    }

    render();
}

function handleBoardEnter() {
    inBoard = true;
}

function handleBoardLeave() {
    inBoard = false;
}

function handleBoardMove(thisCoord) {
    if (takes.find((c) => c.x === thisCoord.x && c.y === thisCoord.y) === undefined) {
        coord = thisCoord;
    }
}

function handleBoardSelect() {
    myTurn = false;
    console.log(`Select [${coord.x},${coord.y}]`);
    ParentToMessage({type: 'player_selected', data : coord});
}

function OmokBoard({ takes }) {
    function render() {
        const omokBoard = document.querySelector(".omokboard");
        omokBoard.innerHTML = `
      ${myTurn ? CoordSelectArea(handleBoardEnter,handleBoardMove,handleBoardLeave,handleBoardSelect) : ""}
      ${takes.map((takes, index) => MemoriedStone(index))}
      ${takes.length > 0 ? MemoriedStone(takes.length - 1, true) : ""}
      ${myTurn && inBoard ? MemoriedStone(takes.length % 2 === 0 ? "black" : "white", "hint") : ""}
    `;
    }
    render();
}

function createStone({ type, x, y }) {
    let material = "";
    type.forEach((m) => {
        switch (m) {
            case "black":
                material += " omokboard__stone--black";
                break;
            case "white":
                material += " omokboard__stone--white";
                break;
            case "hint":
                material += " omokboard__stone--hint";
                break;
            case "prev":
                material += " omokboard__stone--prev";
                break;
        }
    });

    const stoneElement = document.createElement("div");
    stoneElement.className = `omokboard__stone ${material}`;
    stoneElement.style.left = `${x * BOARD_SPACE + BOARD_OFFSET}%`;
    stoneElement.style.top = `${y * BOARD_SPACE + BOARD_OFFSET}%`;
    stoneElement.setAttribute("key", `${x}${y}`);

    return stoneElement;
}

const MemoriedStone = (()=> {
    const memoCache = {};
    function memoizeStone(props) {
        const key = JSON.stringify(props);
        if (memoCache[key]) {
            return memoCache[key];
        } else {
            const stoneElement = createStone(props);
            memoCache[key] = stoneElement;
            return stoneElement;
        }
    }
    return memoizeStone;
})();

function CoordSelectArea(onBoardEnter,onBoardMove,onBoardLeave,onBoardSelect) {
    function getCoord(event) {
        let coordX = 0;
        let coordY = 0;

        if (!isMobile) {
            const percentX = (event.offsetX * 100.0) / event.target.clientWidth;
            const percentY = (event.offsetY * 100.0) / event.target.clientHeight;

            coordX = parseInt((percentX - BOARD_OFFSET) / BOARD_SPACE + 0.5);
            coordY = parseInt((percentY - BOARD_OFFSET) / BOARD_SPACE + 0.5);
        } else {
            const bcr = event.target.getBoundingClientRect();
            const x = event.touches[0].clientX - bcr.x;
            const y = event.touches[0].clientY - bcr.y;

            const percentX = (x * 100.0) / event.target.clientWidth;
            const percentY = (y * 100.0) / event.target.clientHeight;
            coordX = parseInt((percentX - BOARD_OFFSET) / BOARD_SPACE + 0.5);
            coordY = parseInt((percentY - BOARD_OFFSET) / BOARD_SPACE - 1.5);
        }

        if (coordX < 0) coordX = 0;
        if (coordY < 0) coordY = 0;

        if (coordX > 18) coordX = 18;
        if (coordY > 18) coordY = 18;

        return {
            x: coordX,
            y: coordY,
        };
    }

    function onMouseEnter() {
        onBoardEnter();
    }

    function onMouseMove(event) {
        onBoardMove(getCoord(event));
    }

    function onMouseLeave() {
        onBoardLeave();
    }

    function onMouseClick() {
        onBoardSelect();
    }

    function onTouchStart(event) {
        onBoardEnter();
        onBoardMove(getCoord(event));
    }

    function onTouchMove(event) {
        onBoardMove(getCoord(event));
    }

    function onTouchEnd(event) {
        onBoardLeave();
        onBoardSelect();
    }

    const omokboardCoord = document.createElement('div');
    omokboardCoord.className = "omokboard__coord";
    omokboardCoord.addEventListener("mouseenter", onMouseEnter);
    omokboardCoord.addEventListener("mousemove", onMouseMove);
    omokboardCoord.addEventListener("mouseleave", onMouseLeave);
    omokboardCoord.addEventListener("click", onMouseClick);
    omokboardCoord.addEventListener("touchstart", onTouchStart);
    omokboardCoord.addEventListener("touchmove", onTouchMove);
    omokboardCoord.addEventListener("touchend", onTouchEnd);

    return omokboardCoord;
}