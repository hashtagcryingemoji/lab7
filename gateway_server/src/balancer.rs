use lab7_shared::{read_frame, write_frame, Request, Response};
use tokio::net::TcpStream;

#[derive(Clone)]
struct Node {
    host: String,
    port: u16,
}

#[derive(Default)]
pub struct Balancer {
    nodes: Vec<Node>,
    counter: usize,
}

impl Balancer {
    pub async fn handle(&mut self, request: Request) -> Response {
        match request {
            Request::HiBalancer { host, port } => {
                self.register_server(host, port);
                Response::Pong
            }
            request => self.proxy(request).await,
        }
    }

    fn register_server(&mut self, host: String, port: u16) {
        if !self
            .nodes
            .iter()
            .any(|node| node.host == host && node.port == port)
        {
            println!("Registered server {host}:{port}");
            self.nodes.push(Node { host, port });
        }
    }

    async fn proxy(&mut self, request: Request) -> Response {
        if self.nodes.is_empty() {
            return Response::Error {
                message: "Нет серверов".to_string(),
            };
        }

        let node = self.nodes[self.counter % self.nodes.len()].clone();
        self.counter += 1;

        match TcpStream::connect((node.host.as_str(), node.port)).await {
            Ok(mut stream) => {
                if let Err(err) = write_frame(&mut stream, &request).await {
                    eprintln!("{err:?}");
                    return Response::Error {
                        message: "Server unavailable".to_string(),
                    };
                }
                match read_frame::<Response, _>(&mut stream).await {
                    Ok(response) => response,
                    Err(err) => {
                        eprintln!("{err:?}");
                        Response::Error {
                            message: "Server unavailable".to_string(),
                        }
                    }
                }
            }
            Err(err) => {
                eprintln!("{err:?}");
                Response::Error {
                    message: "Server unavailable".to_string(),
                }
            }
        }
    }
}
